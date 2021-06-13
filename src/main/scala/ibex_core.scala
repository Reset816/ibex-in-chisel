import Chisel.{Cat, MuxLookup, is, switch}
import chisel3._
import chisel3.experimental.ChiselEnum

class ibex_core extends Module {
  val io = (new Bundle {
    val boot_addr_i = Input(UInt(32.W))

    // Data memory interface
    val data_addr_o: UInt = Output(UInt(32.W)) // LSU 传给内存需要操作的地址
    val data_w_req_o: Bool = Output(Bool()) // LSU 请求内存写操作
    val data_w_o: UInt = Output(UInt(32.W)) // LSU传给内存需要写的数据
    val data_r_req_o: Bool = Output(Bool()) // LSU 请求内存读操作
    val data_r_i: UInt = Input(UInt(32.W)) // 内存返回给 LSU 的数据
    val read_data_vaild_i: Bool = Input(Bool()) // 内存返回的数据是否有效

    val core_busy_o: Bool = Output(Bool())
  })

    ///////////////////////////////
    //    IF-Stage and ID-Stage  //
    ///////////////////////////////
    val if_stage = Module(new ibex_if_stage)
    val id_stage =  Module(new ibex_id_stage)
    val pc_if = Wire(UInt(32.W)) // Unused
    val instr_id_done = Wire(Bool()) // Unused
    val if_busy = Wire(Bool()) // Used outside core
    val ctrl_busy = Wire(Bool()) // Used outside core
    pc_if := if_stage.io.pc_if_o
    if_busy := if_stage.io.if_busy_o
    ctrl_busy := id_stage.io.ctrl_busy
    instr_id_done := id_stage.io.instr_id_done_o
    if_stage.io.req_i <> id_stage.io.instr_req_o
    if_stage.io.boot_addr_i <> io.boot_addr_i
    if_stage.io.instr_valid_id_o <> id_stage.io.instr_valid_i
    if_stage.io.instr_rdata_id_o <> id_stage.io.instr_rdata_i
    if_stage.io.instr_rdata_alu_id_o <> id_stage.io.instr_rdata_alu_i
    if_stage.io.pc_id_o <> id_stage.io.pc_id_i
    if_stage.io.instr_valid_clear_i <> id_stage.io.instr_valid_clear_o
    if_stage.io.pc_set_i <> id_stage.io.pc_set_o
    if_stage.io.pc_mux_i <> id_stage.io.pc_mux_o
    if_stage.io.branch_target_ex_i <> alu.io.adder_result_ext_o
    if_stage.io.id_in_ready_i <> id_stage.io.id_in_ready_o

    id_stage.io.ex_valid_i := true.B // ALU 仅支持单周期指令
    id_stage.io.lsu_resp_valid_i := lsu_valid

    ///////////////////
    //      ALU      //
    ///////////////////
    val alu = Module(new ibex_alu)

    alu.io.operator_i <> id_stage.alu_operator_ex_o
    alu.io.operand_a_i <> id_stage.alu_operand_a_ex_o
    alu.io.operand_b_i <> id_stage.alu_operand_b_ex_o
    alu.io.instr_first_cycle_i <> id_stage.io.instr_first_cycle_id_o
    alu.io.comparison_result_o <> id_stage.io.branch_decision_i
    alu.io.result_o <> id_stage.io.result_ex_i


    //////////////////
    //     LSU      //
    //////////////////
    val lsu = Module(new LSU)
    val lsu_valid := ~lsu.io.lsu_busy
    lsu.io.addr_i <> alu.io.adder_result_ext_o
    lsu.io.write_enable <> id_stage.io.lsu_we_o
    lsu.io.data_w_i <> id_stage.io.lsu_wdata_o
    lsu.read_req <> id_stage.io.lsu_req_o
    lsu.io.data_addr_o <> io.data_addr_o
    lsu.io.data_w_req_o <> io.data_w_req_o
    lsu.io.data_w_o <> io.data_w_o
    lsu.io.data_r_req_o <> io.data_r_req_o
    lsu.io.data_r_i <> io.data_r_i
    lsu.io.read_data_vaild_i <> io.read_data_vaild_i



    /////////////////////////////
    //      Register File      //
    /////////////////////////////
    val regs = Module(new ibex_register_file_fpga)
    regs.io.test_en_i := false.B // Unused port
    regs.io.dummy_instr_id_i := false.B // Unused port

    regs.io.waddr_a_i <> id_stage.io.rf_waddr_id_o
    regs.io.we_a_i := rf_we
    regs.io.wdata_a_i := rf_wdata
    regs.io.raddr_a_i <> id_stage.io.rf_waddr_id_o
    regs.io.rdata_a_o <> id_stage.io.rf_rdata_a_i
    regs.io.raddr_b_i <> id_stage.io.rf_raddr_b_o
    regs.io.rdata_b_o <> id_stage.io.rf_rdata_b_i

    val rf_wdata_id = Wire(UInt(32.W))
    val rf_we_id = Wire(Bool())
    val rf_wdata_lsu = Wire(UInt(32.W))
    val rf_we_lsu = Wire(Bool())
    val rf_wdata = Wire(UInt(32.W))
    val rf_we = Wire(Bool())
    rf_wdata_id := id_stage.io.rf_wdata_id_o
    rf_we_id := id_stage.io.rf_we_id_o
    rf_wdata_lsu := io.data_r_i
    rf_we_lsu := lsu.io.data_vaild
    rf_wdata := Mux(rf_we_id == true.B, rf_wdata_id, rf_wdata_lsu)
    rf_we := rf_we_id | rf_we_lsu

    /////////////////////////////
    //          Mis           //
    /////////////////////////////
    io.core_busy_o := if_stage.io.if_busy_o | id_stage.io.ctrl_busy_o | lsu.io.lsu_busy
}
