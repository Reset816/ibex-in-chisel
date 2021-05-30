//import Chisel.{Cat, is, switch}
//import chisel3._
//import chisel3.experimental.ChiselEnum
//
//class ibex_load_store_unit extends Module {
//  val io = IO(new Bundle {
//    val clk_i: Clock = Input(Clock())
//    val rst_ni: UInt = Input(UInt(1.W))
//
//    //data interface
//    val data_req_o: UInt = Output(UInt(1.W))
//    val data_gnt_i: UInt = Input(UInt(1.W))
//    val data_rvalid_i: UInt = Input(UInt(1.W))
//
//    val data_addr_o: UInt = Output(UInt(32.W))
//    val data_we_o: UInt = Output(UInt(1.W))
//    val data_be_o: UInt = Output(UInt(4.W))
//    val data_wdata_o: UInt = Output(UInt(32.W))
//    val data_rdata_i: UInt = Input(UInt(32.W))
//
//    //signals to/from ID/IE stage
//    val lsu_we_i: UInt = Input(UInt(1.W)) // write enable                     -> from ID/EX
//    val lsu_type_i: UInt = Input(UInt(2.W)) // data type: word, half word, byte -> from ID/EX
//    val lsu_wdata_i: UInt = Input(UInt(32.W)) // data to write to memory          -> from ID/EX
//    val lsu_sign_ext_i: UInt = Input(UInt(1.W)) // sign extension                   -> from ID/EX
//
//    val lsu_rdata_o: UInt = Output(UInt(32.W)) // requested data                   -> to ID/EX
//    val lsu_rdata_valid_o: UInt = Output(UInt(1.W))
//    val lsu_req_i: UInt = Input(UInt(1.W)) // data request                     -> from ID/EX
//
//    val adder_result_ex_i: UInt = Input(UInt(32.W)) // address computed in ALU          -> from ID/EX
//
//    val addr_incr_req_o: UInt = Output(UInt(1.W)) // request address increment for
//    // misaligned accesses              -> to ID/EX
//    val addr_last_o: UInt = Output(UInt(32.W)) // address of last transaction      -> to controller
//    // -> mtval
//    // -> AGU for misaligned accesses
//
//    val lsu_req_done_o: UInt = Output(UInt(1.W)) // Signals that data request is complete
//    // (only need to await final data
//    // response)                        -> to ID/EX
//
//    val lsu_resp_valid_o: UInt = Output(UInt(1.W)) // LSU has response from transaction -> to ID/EX
//
//    //exception signals
//    val busy_o: UInt = Output(UInt(1.W))
//    val perf_load_o: UInt = Output(UInt(1.W))
//    val perf_store_o: UInt = Output(UInt(1.W))
//  })
//
//  var data_addr: UInt = Wire(UInt(32.W))
//  var data_addr_w_aligned: UInt = Wire(UInt(32.W))
//  var addr_last_q: UInt = Wire(UInt(32.W))
//
//  var addr_update: Bool = Wire(Bool())
//  var ctrl_update: Bool = Wire(Bool())
//  var rdata_update: Bool = Wire(Bool())
//  var rdata_q: UInt = Wire(UInt(24.W))
//  var rdata_offset_q: UInt = Wire(UInt(2.W))
//  var data_type_q: UInt = Wire(UInt(2.W))
//  var data_sign_ext_q: Bool = Wire(Bool())
//  var data_we_q: Bool = Wire(Bool())
//
//  var data_offset: UInt = Wire(UInt(2.W)) // mux control for data to be written to memory
//  var data_be: UInt = Wire(UInt(4.W))
//  var data_wdata: UInt = Wire(UInt(32.W))
//  var data_rdata_ext: UInt = Wire(UInt(32.W))
//
//  var split_misaligned_access: Bool = Wire(Bool())
//  var handle_misaligned_q: Bool = Wire(Bool())
//  var handle_misaligned_d: Bool = Wire(Bool()) // high after receiving grant for first
//  // part of a misaligned access
//
//  object ls_fsm_e extends ChiselEnum {
//    val IDLE = Value(3.U)
//    val WAIT_GNT_MIS = Value(3.U)
//    val WAIT_RVALID_MIS = Value(3.U)
//    val WAIT_GNT = Value(3.U)
//    val WAIT_RVALID_MIS_GNTS_DONE = Value(3.U)
//  }
//
//  var ls_fsm_cs: ls_fsm_e.Type = Wire(ls_fsm_e())
//  var ls_fsm_ns: ls_fsm_e.Type = Wire(ls_fsm_e())
//
//  data_addr := io.adder_result_ex_i
//  data_offset := data_addr(1, 0)
//
//  ///////////////////
//  // BE generation //
//  ///////////////////
//  {
//    switch(io.lsu_type_i) {
//      data_be := "b1111".U(4.W)
//      is("b00".U(2.W)) {
//        when(!handle_misaligned_q) {
//          data_be := "b1111".U(4.W)
//          switch(data_offset) {
//            is("b00".U(2.W)) {
//              data_be := "b1111".U(4.W)
//            }
//            is("b01".U(2.W)) {
//              data_be := "b1110".U(4.W)
//            }
//            is("b10".U(2.W)) {
//              data_be := "b1100".U(4.W)
//            }
//            is("b11".U(2.W)) {
//              data_be := "b1000".U(4.W)
//            }
//          }
//        }.otherwise {
//          data_be := "b1111".U(4.W)
//          switch(data_offset) {
//            is("b00".U(2.W)) {
//              data_be := "b0000".U(4.W)
//            }
//            is("b01".U(2.W)) {
//              data_be := "b0001".U(4.W)
//            }
//            is("b10".U(2.W)) {
//              data_be := "b0011".U(4.W)
//            }
//            is("b11".U(2.W)) {
//              data_be := "b1111".U(4.W)
//            }
//          }
//        }
//      }
//    }
//  }
//  // registers for transaction control
//  withClock(io.clk_i) {
//    when(io.rst_ni === false.B) {
//      rdata_offset_q := "h0".U(2.W)
//      data_type_q := "h0".U(2.W)
//      data_sign_ext_q := "h0".U(1.W)
//      data_we_q := "h0".U(1.W)
//    }.otherwise(ctrl_update === true.B) {
//      rdata_offset_q := data_offset
//      data_type_q := io.lsu_type_i
//      data_sign_ext_q := io.lsu_sign_ext_i
//      data_we_q := io.lsu_we_i
//    }
//  }
//
//  // select word, half word or byte sign extended version
//  data_rdata_ext := rdata_w_ext
//  ls_fsm_ns := ls_fsm_cs
//  io.data_req_o := "b0".U(1.W)
//  io.addr_incr_req_o := "b0".U(1.W)
//  handle_misaligned_d := handle_misaligned_q
//
//  addr_update := "b0".U(1.W)
//  ctrl_update := "b0".U(1.W)
//  rdata_update := "b0".U(1.W)
//
//  io.perf_load_o := "b0".U(1.W)
//  io.perf_store_o := "b0".U(1.W)
//
//  switch(ls_fsm_cs) {
//    ls_fsm_ns := ls_fsm_e.IDLE
//    is(ls_fsm_e.IDLE) {
//      when(io.lsu_req_i === true.B) {
//        io.data_req_o := "b1".U(1.W)
//        io.perf_load_o := ~io.lsu_we_i
//        io.perf_store_o := io.lsu_we_i
//        when(io.data_gnt_i === true.B) {
//          //???
//          addr_update := "b1".U(1.W)
//          handle_misaligned_d := split_misaligned_access
//          ls_fsm_ns := Mux(split_misaligned_access, ls_fsm_e.WAIT_RVALID_MIS, ls_fsm_e.IDLE)
//        }.otherwise {
//          ls_fsm_ns := Mux(split_misaligned_access, ls_fsm_e.WAIT_RVALID_MIS, ls_fsm_e.WAIT_GNT)
//        }
//      }
//    }
//    is(ls_fsm_e.WAIT_GNT_MIS) {
//      io.data_req_o := "b1".U(1.W)
//      when(io.data_gnt_i) {
//        addr_update := "b1".U(1.W)
//        ctrl_update := "b1".U(1.W)
//        handle_misaligned_d := "b1".U(1.W)
//        ls_fsm_ns := ls_fsm_e.WAIT_GNT_MIS
//      }
//    }
//    is(ls_fsm_e.WAIT_RVALID_MIS) {
//      io.data_req_o := "b1".U(1.W)
//      io.addr_incr_req_o := "b1".U(1.W)
//      when(io.data_gnt_i === true.B) {
//        ls_fsm_ns := ls_fsm_e.WAIT_RVALID_MIS_GNTS_DONE
//        handle_misaligned_d := "b0".U(1.W)
//      }
//    }
//    is(ls_fsm_e.WAIT_GNT) {
//      io.addr_incr_req_o := handle_misaligned_q
//      io.data_req_o := "b1".U(1.W)
//      when() {
//        ctrl_update := "b1".U(1.W)
//        //addr_update
//        ls_fsm_ns := ls_fsm_e.IDLE
//        handle_misaligned_d := "b0".U(1.W)
//      }
//    }
//    is(ls_fsm_e.WAIT_RVALID_MIS_GNTS_DONE) {
//      io.addr_incr_req_o := "b1".U(1.W)
//      when(io.data_rvalid_i === true.B) {
//        addr_update := ~data_we_q
//        rdata_update := ~data_we_q
//        ls_fsm_ns := ls_fsm_e.IDLE
//      }
//    }
//  }
//  io.lsu_req_done_o := (io.lsu_req_i | (ls_fsm_cs =/= ls_fsm_e.IDLE)) & (ls_fsm_cs === ls_fsm_e.IDLE)
//  // registers for FSM
//  withClock(io.clk_i) {
//    when(io.rst_ni === false.B) {
//      ls_fsm_cs := ls_fsm_e.IDLE
//      handle_misaligned_q := "b0".U(1.W)
//    }.otherwise {
//      ls_fsm_cs := ls_fsm_ns
//      handle_misaligned_q := handle_misaligned_d
//    }
//  }
//  /////////////
//  // Outputs //
//  /////////////
//  io.lsu_resp_valid_o := (io.data_rvalid_i) & (ls_fsm_cs === ls_fsm_e.IDLE)
//  io.lsu_rdata_valid_o := (ls_fsm_cs === ls_fsm_e.IDLE) & io.data_rvalid_i & (~data_we_q)
//
//  // output to register file
//  io.lsu_rdata_o := data_rdata_ext
//
//  // output data address must be word aligned
//  data_addr_w_aligned := Cat(data_addr(31, 2), "b00".U(2.W))
//
//  // output to data interface
//  io.data_addr_o := data_addr_w_aligned
//  io.data_wdata_o := data_wdata
//  io.data_we_o := io.lsu_we_i
//  io.data_be_o := data_be
//
//  // output to ID stage: mtval + AGU for misaligned transactions
//  io.addr_last_o := addr_last_q
//
//  // Signal a load or store error depending on the transaction type outstanding
//  io.busy_o := (ls_fsm_cs =/= ls_fsm_e.IDLE)
//
//  //////////
//  // FCOV //
//  //////////
//  //??看不懂下面的
//
//
//  ////////////////
//  // Assertions //
//  ////////////////
//}
//
