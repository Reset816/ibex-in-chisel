import chisel3._
import chisel3.util.{Cat, Fill, MuxLookup, is, switch}

class ibex_decoder(
                    val RV32E: Bool = false.B,
                    val RV32M: rv32m_e.Type = rv32m_e.RV32MFast,
                    val RV32B: rv32b_e.Type = rv32b_e.RV32BNone,
                    val BranchTargetALU: Bool = false.B,
                  ) extends Module {
  val io = IO(new Bundle {
    //        val clk_i: UInt = Input(UInt(1.W))
    //        val rst_ni: UInt = Input(UInt(1.W))

    // to/from controller
    //    val illegal_insn_o: Bool = Output(Bool())
    //    val ebrk_insn_o: Bool = Output(Bool())
    //    val mret_insn_o: Bool = Output(Bool())

    //    val dret_insn_o: Bool = Output(Bool())
    //    val ecall_insn_o: Bool = Output(Bool())
    //    val wfi_insn_o: Bool = Output(Bool())
    val jump_set_o: Bool = Output(Bool())
    val branch_taken_i: Bool = Input(Bool())
    val icache_inval_o: Bool = Output(Bool())

    // from IF-ID pipeline register
    val instr_first_cycle_i: Bool = Input(Bool())
    val instr_rdata_i: UInt = Input(UInt(32.W))
    val instr_rdata_alu_i: UInt = Input(UInt(32.W))
    //    val illegal_c_insn_i: Bool = Input(Bool())

    // immediates
    val imm_a_mux_sel_o: imm_a_sel_e.Type = Output(imm_a_sel_e())
    var imm_b_mux_sel_o: imm_b_sel_e.Type = Output(imm_b_sel_e())
    //    val bt_a_mux_sel_o: op_a_sel_e.Type = Output(op_a_sel_e())
    //    val bt_b_mux_sel_o: imm_a_sel_e.Type = Output(imm_a_sel_e())
    val imm_i_type_o: UInt = Output(UInt(32.W))
    val imm_s_type_o: UInt = Output(UInt(32.W))
    val imm_b_type_o: UInt = Output(UInt(32.W))
    val imm_u_type_o: UInt = Output(UInt(32.W))
    val imm_j_type_o: UInt = Output(UInt(32.W))
    //    val zimm_rs1_type_o: UInt = Output(UInt(32.W))

    // register file
    val rf_wdata_sel_o: rf_wd_sel_e.Type = Output(rf_wd_sel_e())
    val rf_we_o: Bool = Output(Bool())
    val rf_raddr_a_o: UInt = Output(UInt(5.W))
    val rf_raddr_b_o: UInt = Output(UInt(5.W))
    val rf_waddr_o: UInt = Output(UInt(5.W))
    val rf_ren_a_o: Bool = Output(Bool())
    val rf_ren_b_o: Bool = Output(Bool())

    //ALU
    val alu_operator_o: alu_op_e.Type = Output(alu_op_e())
    val alu_op_a_mux_sel_o: op_a_sel_e.Type = Output(op_a_sel_e())
    val alu_op_b_mux_sel_o: op_b_sel_e.Type = Output(op_b_sel_e())

    val alu_multicycle_o: Bool = Output(Bool())
    // MULT & DIV

    // CSRs

    // LSU
    val data_req_o: Bool = Output(Bool())
    val data_we_o: Bool = Output(Bool())
    //    val data_type_o: UInt = Output(UInt(2.W))
    val data_sign_extension_o: Bool = Output(Bool())

    //jump/branches
    val jump_in_dec_o: Bool = Output(Bool())
    val branch_in_dec_o: Bool = Output(Bool())

  })
  //  var illeagal_insn: Bool = Wire(Bool())
  //  var illeagal_reg_rv32e: Bool = Wire(Bool())
  //  var csr_illeagal: Bool = Wire(Bool())
  var rf_we: Bool = Wire(Bool())

  var instr: UInt = Wire(UInt(32.W))
  var instr_alu = Wire(UInt(32.W))
  //  var unused_instr_alu = Wire(UInt(10.W))

  // Source/Destination register instruction index
  var instr_rs1: UInt = Wire(UInt(5.W))
  var instr_rs2: UInt = Wire(UInt(5.W))
  var instr_rs3: UInt = Wire(UInt(5.W))
  var instr_rd: UInt = Wire(UInt(5.W))

  var use_rs3_d: Bool = Wire(Bool())
  var use_rs3_q: Bool = Wire(Bool())
  //  var csr_op: csr_op_e.Type = Wire(csr_op_e())

  var opcode: opcode_e.Type = Wire(opcode_e())
  var opcode_alu: opcode_e.Type = Wire(opcode_e())

  // To help timing the flops containing the current instruction are replicated to reduce fan-out.
  // instr_alu is used to determine the ALU control logic and associated operand/imm select signals
  // as the ALU is often on the more critical timing paths. instr is used for everything else.
  instr := io.instr_rdata_i
  instr_alu := io.instr_rdata_alu_i

  //////////////////////////////////////
  // Register and immediate selection //
  //////////////////////////////////////

  // immediate extraction and sign extension
  io.imm_i_type_o := Cat(Fill(20, instr(31)), instr(31, 20))
  io.imm_s_type_o := Cat(Fill(20, instr(31)), instr(31, 25), instr(11, 7))
  io.imm_b_type_o := Cat(Fill(19, instr(31)), instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W))
  io.imm_u_type_o := Cat(instr(31, 12), 0.U(12.W))
  io.imm_j_type_o := Cat(Fill(12, instr(31)), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W))

  // immediate for CSR manipulation (zero extended)
  //****
  //if(RV32B)
  use_rs3_q := use_rs3_d
  // source registers
  instr_rs1 := instr(19, 15).asUInt()
  instr_rs2 := instr(24, 20).asUInt()
  instr_rs3 := instr(31, 27).asUInt()
  io.rf_raddr_a_o := Mux(use_rs3_q & ~io.instr_first_cycle_i, instr_rs3, instr_rs1)
  io.rf_raddr_b_o := instr_rs2

  // destination register
  instr_rd := instr(11, 7).asUInt()
  io.rf_waddr_o := instr_rd

  ///////////////////////
  // CSR operand check //
  ///////////////////////
  //****


  /////////////
  // Decoder //
  /////////////

  {
    io.jump_in_dec_o := 0.U(1.W)
    io.jump_set_o := 0.U(1.W)
    io.branch_in_dec_o := 0.U(1.W)
    io.icache_inval_o := 0.U(1.W)

    io.rf_wdata_sel_o := rf_wd_sel_e.RF_WD_EX
    rf_we := 0.U(1.W)
    io.rf_ren_a_o := 0.U(1.W)
    io.rf_ren_b_o := 0.U(1.W)

    io.data_we_o := 0.U(1.W)
    //    io.data_type_o := 0.U(2.W)
    io.data_sign_extension_o := 0.U(1.W)
    io.data_req_o := 0.U(1.W)

    //    illeagal_insn := 0.U(1.W)
    //    io.ebrk_insn_o := 0.U(1.W)
    //    io.mret_insn_o := 0.U(1.W)
    //    io.dret_insn_o := 0.U(1.W)
    //    io.ecall_insn_o := 0.U(1.W)
    //    io.wfi_insn_o := 0.U(1.W)
    opcode := opcode_e(instr(6, 0)) //判断指令为哪一大类
    switch(opcode) {
      ///////////
      // Jumps //
      ///////////

      is(opcode_e.OPCODE_JAL) {
        io.jump_set_o := 1.U(1.W)

        when(io.instr_first_cycle_i) {
          // Calculate jump target (and store PC + 4 if BranchTargetALU is configured)
          rf_we := BranchTargetALU
          io.jump_set_o := 1.U(1.W)
        }.otherwise {
          // Calculate and store PC+4
          rf_we := 1.U(1.W)
        }
      }

      is(opcode_e.OPCODE_JALR) {
        io.jump_set_o := 1.U(1.W)

        when(io.instr_first_cycle_i) {
          // Calculate jump target (and store PC + 4 if BranchTargetALU is configured)
          rf_we := BranchTargetALU
          io.jump_set_o := 1.U(1.W)
        }.otherwise {
          // Calculate and store PC+4
          rf_we := 1.U(1.W)
        }
        //        when(instr(14, 12) =/= 0.U(3)) { // 判断是否为非法指令
        //          illeagal_insn := 1.U(1.W)
        //        }

        io.rf_ren_a_o := 1.U(1.W) // 需要调用寄存器a
      }

      is(opcode_e.OPCODE_BRANCH) { // Branch 无条件跳转
        io.branch_in_dec_o := 1.U(1.W)

        // Check branch condition selection
        //        // 判断是否为非法指令
        //        {
        //          illeagal_insn := 1.U(1.W)
        //          switch(instr(14, 12)) {
        //            is("b000".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //            is("b001".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //            is("b100".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //            is("b101".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //            is("b110".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //            is("b111".U(3.W)) {
        //              illeagal_insn := 0.U(1.W)
        //            }
        //          }
        //        }

        io.rf_ren_a_o := 1.U(1.W) // 需要调用寄存器a
        io.rf_ren_b_o := 1.U(1.W) // 需要调用寄存器b
      }

      ////////////////
      // Load/store //
      ////////////////

      is(opcode_e.OPCODE_STORE) {
        io.rf_ren_a_o := 1.U(1.W)
        io.rf_ren_b_o := 1.U(1.W)
        io.data_req_o := 1.U(1.W) // 是否需要访问数据
        io.data_we_o := 1.U(1.W) // 是否可以写回数据

        //        when(instr(14)) {
        //          illeagal_insn := 1.U(1.W)
        //        }

        //        // 判断是否为半字长
        //        {
        //          illeagal_insn := 1.U(1.W)
        //          switch(instr(13, 12).asUInt()) {
        //            is("b00".U(2.W)) {
        //              io.data_type_o := "b10".U(2.W) // sb
        //            }
        //            is("b01".U(2.W)) {
        //              io.data_type_o := "b01".U(2.W) // sh
        //            }
        //            is("b10".U(2.W)) {
        //              io.data_type_o := "b00".U(2.W) // sw
        //            }
        //          }
        //        }
      }

      is(opcode_e.OPCODE_LOAD) {
        io.rf_ren_a_o := 1.U(1.W)
        io.data_req_o := 1.U(1.W)
        //        io.data_type_o := "b00".U(2.W)

        // sign/zero extension
        io.data_sign_extension_o := ~instr(14) // 判断是否进行store/load操作

        //        {
        //          illeagal_insn := 1.U(1.W)
        //          switch(instr(13, 12)) {
        //            is("b00".U(2.W)) {
        //              io.data_type_o := "b10".U(2.W)
        //            }
        //            is("b01".U(2.W)) {
        //              io.data_type_o := "b01".U(2.W)
        //            }
        //            is("b10".U(2.W)) {
        //              io.data_type_o := "b00".U(2.W)
        //              when(instr(14)) {
        //                illeagal_insn := 1.U(1.W)
        //              }
        //            }
        //          }
        //        }
      }

      /////////
      // ALU //
      /////////

      is(opcode_e.OPCODE_LUI) { // Load Upper Immediate 存立即数于高位寄存器
        rf_we := 1.U(1.W)
      }
      is(opcode_e.OPCODE_AUIPC) { // Add Upper Immediate to PC PC增加一个立即数的值
        rf_we := 1.U(1.W)
      }
      is(opcode_e.OPCODE_OP_IMM) { // Register-Immediate ALU Operations 传入ALU的数据，一个为寄存器一个为立即数
        io.rf_ren_a_o := 1.U(1.W)
        rf_we := 1.U(1.W)

        //判断是否为非法指令
        //        switch(instr(14, 12)) {
        //          is("b000".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //          is("b010".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //          is("b011".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //          is("b100".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //          is("b110".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //          is("b111".U(3.W)) {
        //            illeagal_insn := 0.U(1.W)
        //          }
        //
        //          is("b001".U(3.W)) {
        //            switch(instr(31, 27).asUInt()) {
        //              is("b0_0000".U(5.W)) {
        //                illeagal_insn := Mux(instr(26, 25).asBool(), 0.U(1.W), 1.U(1.W))
        //              }
        //              is("b0_0100".U(5.W)) {
        //                illeagal_insn := Mux(RV32B =/= rv32b_e.RV32BNone, 0.U(1.W), 1.U(1.W))
        //              }
        //              is("b0_1001".U(5.W)) {
        //                illeagal_insn := Mux(RV32B =/= rv32b_e.RV32BNone, 0.U(1.W), 1.U(1.W))
        //              }
        //              is("b0_0101".U(5.W)) {
        //                illeagal_insn := Mux(RV32B =/= rv32b_e.RV32BNone, 0.U(1.W), 1.U(1.W))
        //              }
        //              is("b0_1101".U(5.W)) {
        //                illeagal_insn := Mux(RV32B =/= rv32b_e.RV32BNone, 0.U(1.W), 1.U(1.W))
        //              }
        //              is("b0_0001".U(5.W)) {
        //                when(instr(26) === false.B) {
        //                  illeagal_insn := Mux(RV32B === rv32b_e.RV32BNone, 0.U(1.W), 1.U(1.W))
        //                }.otherwise {
        //                  illeagal_insn := 1.U(1.W)
        //                }
        //              }
        //              is("b0_1100".U(5.W)) {
        //                switch(instr(26, 20).asUInt()) {
        //                  is("b000_0000".U(7.W)) {}
        //                  is("b000_0001".U(7.W)) {}
        //                  is("b000_0010".U(7.W)) {}
        //                  is("b000_0100".U(7.W)) {}
        //                  is("b000_0101".U(7.W)) {}
        //                  is("b001_0000".U(7.W)) {}
        //                  is("b001_0010".U(7.W)) {}
        //                  is("b001_1001".U(7.W)) {}
        //                  is("b001_1010".U(7.W)) {}}
        //              }
        //            }
        //          }
        //          is("b101".U(3.W)) {
        //            if (instr(26, 26).asUInt()) {
        //              //RV32B
        //            }
        //            else {
        //              switch(instr(31, 27).asUInt()) {
        //                is("b0_0000".U(5.W)) {}
        //                is("b0_1000".U(5.W)) {
        //                  illeagal_insn := Mux(instr(26, 25).asBool(), 0.U(1.W), 1.U(1.W))
        //                }
        //                is("b0_0100".U(5.W)) {}
        //                is("b0_1100".U(5.W)) {}
        //                is("b0_1001".U(5.W)) {
        //                  //RV32B
        //                }
        //                is("b0_1101".U(5.W)) {
        //                  //RV32B
        //                }
        //                is("b0_0101".U(5.W)) {
        //                  //RV32B
        //                }
        //                is("b0_0001".U(5.W)) {
        //                  //RV32B
        //                }
        //                //default
        //              }
        //            }
        //          }
        //          //default
        //        }
      }

      is(opcode_e.OPCODE_OP) { // Register-Register ALU operation 将两个寄存器的数据传入ALU
        io.rf_ren_a_o := 1.U(1.W)
        io.rf_ren_b_o := 1.U(1.W)
        rf_we := 1.U(1.W)
        //RV32B  if()
      }

      /////////////
      // Special //
      /////////////

      //      is(opcode_e.OPCODE_MISC_MEM) {
      //        switch(instr(14, 12).asUInt()) {
      //          is("b000".U(3.W)) {
      //            // FENCE is treated as a NOP since all memory operations are already strictly ordered.
      //            rf_we := 0.U(1.W)
      //          }
      //          is("b001".U(3.W)) {
      //            // FENCE.I is implemented as a jump to the next PC, this gives the required flushing
      //            // behaviour (iside prefetch buffer flushed and response to any outstanding iside
      //            // requests will be ignored).
      //            // If present, the ICache will also be flushed.
      //            io.jump_in_dec_o := 1.U(1.W)
      //
      //            rf_we := 0.U(1.W)
      //
      //            when (io.instr_first_cycle_i) {
      //              io.jump_set_o := 1.U(1.W)
      //              io.icache_inval_o := 1.U(1.W)
      //            }
      //          }
      //          //default
      //        }
      //      }
      //      is(opcode_e.OPCODE_SYSTEM) {
      //        if (instr(14, 12).asUInt() === "b000".U(3.W)) {
      //          switch(instr(31, 20).asUInt()) {
      //            is("h000".U(12.W)) {
      //              io.ecall_insn_o := 1.U(1.W)
      //            }
      //            is("h001".U(12.W)) {
      //              io.ebrk_insn_o := 1.U(1.W)
      //            }
      //            is("h302".U(12.W)) {
      //              io.mret_insn_o := 1.U(1.W)
      //            }
      //            is("h7b2".U(12.W)) {
      //              io.dret_insn_o := 1.U(1.W)
      //            }
      //            is("h105".U(12.W)) {
      //              io.wfi_insn_o := 1.U(1.W)
      //            }
      //            //default
      //          }
      //          // rs1 and rd must be 0
      //          if () {
      //            illeagal_insn := 1.U(1.W)
      //          }
      //          else {
      //            //csr
      //            io.rf_wdata_sel_o := rf_wd_sel_e.RF_WD_CSR
      //            rf_we := 1.U(1.W)
      //            if (~instr(14, 14).asUInt())
      //              io.rf_ren_a_o := 1.U(1.W)
      //
      //            switch(instr(13, 12).asUInt()) {
      //              is("b01".U(2.W)) {
      //                //csr
      //              }
      //              is("b10".U(2.W)) {
      //                //csr
      //              }
      //              is("b11".U(2.W)) {
      //                //csr
      //              }
      //              //default
      //            }
      //            illeagal_insn := csr_illeagal
      //          }
      //        }
      //      }
    }

    //    // make sure illegal compressed instructions cause illegal instruction exceptions
    //    if (io.illegal_c_insn_i) {
    //      illeagal_insn := 1.U(1.W)
    //    }
    //
    //    if (illeagal_insn) {
    //      rf_we := 0.U(1.W)
    //      io.data_req_o := 0.U(1.W)
    //      io.data_we_o := 0.U(1.W)
    //      io.jump_set_o := 0.U(1.W)
    //      io.jump_in_dec_o := 0.U(1.W)
    //      io.branch_in_dec_o := 0.U(1.W)
    //      //csr
    //    }

  }

  /////////////////////////////
  // Decoder for ALU control //
  /////////////////////////////

  {
    io.alu_operator_o := alu_op_e.ALU_SLTU
    io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_IMM
    io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM

    io.imm_a_mux_sel_o := imm_a_sel_e.IMM_A_ZERO
    io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_I

    //    io.bt_a_mux_sel_o     :=  op_a_sel_e.OP_A_REG_A;
    //    io.bt_b_mux_sel_o     :=  imm_b_sel_e.IMM_B_I;

    opcode_alu := opcode_e(instr(6, 0))

    use_rs3_d := 0.U(1.W)
    io.alu_multicycle_o := 0.U(1.W)
    //    mult_sel_o := 0.U(1.W)
    //    div_sel_o := 0.U(1.W)

    switch(opcode_alu) {

      ///////////
      // Jumps //
      ///////////

      is(opcode_e.OPCODE_JAL) { // Jump and Link
        // Jumps take two cycles without the BTALU
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_CURRPC
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
        io.alu_operator_o := alu_op_e.ALU_ADD
        when(io.instr_first_cycle_i) {
          // Calculate jump target
          io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_J
        }.otherwise {
          // Calculate and store PC+4
          io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_INCR_PC
        }
      }

      is(opcode_e.OPCODE_JALR) {
        // Jumps take two cycles without the BTALU
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
        io.alu_operator_o := alu_op_e.ALU_ADD
        when(io.instr_first_cycle_i) {
          // Calculate jump target
          io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A
          io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_I
        }.otherwise {
          // Calculate and store PC+4
          io.alu_op_a_mux_sel_o := (op_a_sel_e.OP_A_CURRPC)
          io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_INCR_PC
        }

      }

      is(opcode_e.OPCODE_BRANCH) { // Branch
        // Check branch condition selection
        switch(instr_alu(14, 12)) {
          is("b000".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_EQ
          }
          is("b001".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_NE
          }
          is("b100".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_LT
          }
          is("b101".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_GE
          }
          is("b110".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_LTU
          }
          is("b111".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_GEU
          }
        }

        // Without branch target ALU, a branch is a two-stage operation using the Main ALU in both
        // stages

        when(io.instr_first_cycle_i) {
          // First evaluate the branch condition
          io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A
          io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_REG_B
        }.otherwise {
          // Then calculate jump target
          io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_CURRPC
          io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
          // Not-taken branch will jump to next instruction (used in secure mode)// Not-taken branch will jump to next instruction (used in secure mode)
          // 分支预测 不预测默认为0
          io.imm_b_mux_sel_o := Mux(io.branch_taken_i, imm_b_sel_e.IMM_B_I, imm_b_sel_e.IMM_B_INCR_PC)
          io.alu_operator_o := alu_op_e.ALU_ADD
        }
      }

      ////////////////
      // Load/store //
      ////////////////

      is(opcode_e.OPCODE_STORE) {
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_REG_B
        io.alu_operator_o := alu_op_e.ALU_ADD

        when(!instr_alu(14)) {
          // offset from immediate
          io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_I
          io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_REG_B
        }
      }

      is(opcode_e.OPCODE_LOAD) {
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A

        // offset from immediate
        io.alu_operator_o := alu_op_e.ALU_ADD
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_REG_B
        io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_I
      }

      /////////
      // ALU //
      /////////

      is(opcode_e.OPCODE_LUI) { // Load Upper Immediate
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_IMM
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
        io.imm_a_mux_sel_o := imm_a_sel_e.IMM_A_ZERO
        io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_U
        io.alu_operator_o := alu_op_e.ALU_ADD
      }

      is(opcode_e.OPCODE_AUIPC) { // Add Upper Immediate to PC
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_CURRPC
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
        io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_U
        io.alu_operator_o := alu_op_e.ALU_ADD
      }

      is(opcode_e.OPCODE_OP_IMM) { // Register-Immediate ALU Operations
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_IMM
        io.imm_b_mux_sel_o := imm_b_sel_e.IMM_B_I

        switch(instr_alu(14, 12)) {
          is("b000".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_ADD
          }
          is("b010".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_SLT
          }
          is("b011".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_SLTU
          }
          is("b100".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_XOR
          }
          is("b110".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_OR
          }
          is("b111".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_AND
          }
          is("b001".U(3.W)) {
            io.alu_operator_o := alu_op_e.ALU_SLL
          }
          is("b101".U(3.W)) {
            when(instr_alu(31, 27) === "b0_0000".U) {
              io.alu_operator_o := alu_op_e.ALU_SRL
            }.elsewhen(instr_alu(31, 27) === "b0_1000".U) {
              io.alu_operator_o := alu_op_e.ALU_SRA
            }
          }
        }
      }

      is(opcode_e.OPCODE_OP) { // Register-Register ALU operation
        io.alu_op_a_mux_sel_o := op_a_sel_e.OP_A_REG_A
        io.alu_op_b_mux_sel_o := op_b_sel_e.OP_B_REG_B

        when(!instr_alu(26)) {
          io.alu_operator_o := alu_op_e(MuxLookup(Cat(instr_alu(31, 25), instr_alu(14, 12)), 0.U, Array(
            Cat("b000_0000".U(7.W), "b000".U(3.W)) -> alu_op_e.ALU_ADD.asUInt(),
            Cat("b010_0000".U(7.W), "b000".U(3.W)) -> alu_op_e.ALU_SUB.asUInt(),
            Cat("b000_0000".U(7.W), "b010".U(3.W)) -> alu_op_e.ALU_SLT.asUInt(),
            Cat("b000_0000".U(7.W), "b011".U(3.W)) -> alu_op_e.ALU_SLTU.asUInt(),
            Cat("b000_0000".U(7.W), "b100".U(3.W)) -> alu_op_e.ALU_XOR.asUInt(),
            Cat("b000_0000".U(7.W), "b110".U(3.W)) -> alu_op_e.ALU_OR.asUInt(),
            Cat("b000_0000".U(7.W), "b111".U(3.W)) -> alu_op_e.ALU_AND.asUInt(),
            Cat("b000_0000".U(7.W), "b001".U(3.W)) -> alu_op_e.ALU_SLL.asUInt(), // Shift Left Logical
            Cat("b000_0000".U(7.W), "b101".U(3.W)) -> alu_op_e.ALU_SRL.asUInt(), // Shift Right Logical
            Cat("b010_0000".U(7.W), "b101".U(3.W)) -> alu_op_e.ALU_SRA.asUInt()))) // Shift Right Arithmetic
        }
      }
      /////////////
      // Special //
      /////////////
      //      is(opcode_e.OPCODE_MISC_MEM) {}
      //      is(opcode_e.OPCODE_SYSTEM) {}
    }
    //    // do not enable multdiv in case of illegal instruction exceptions
    //    mult_en_o := if(illegal_insn) "b0".U(1.W) else mult_sel_o
    //    div_en_o := if(illegal_insn) "b0".U(1.W) else div_sel_o
    //
    //    // make sure instructions accessing non-available registers in RV32E cause illegal // instruction exceptions
    //
    //    illegal_insn_o := illegal_insn|illegal_reg_rv32e
    //
    // do not propgate regfile write enable if non-available registers are accessed in RV32E
    io.rf_we_o := rf_we

    // Not all bits are used
    //unused_instr_alu := Cat(instr_alu(19,15), instr_alu(11,7))

  }
}

