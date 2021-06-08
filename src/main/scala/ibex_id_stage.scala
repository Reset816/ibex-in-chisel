import Chisel.{Cat, MuxLookup, is, switch}
import chisel3._
import chisel3.experimental.ChiselEnum

class ibex_id_stage extends Module {
  val io = IO(new Bundle {
    val ctrl_busy_o: Bool = Output(Bool())

    // Interface to IF stage
    val instr_valid_i: Bool = Input(Bool()) // from IF-ID pipeline registers
    val instr_rdata_i: Bool = Input(Bool()) // from IF-ID pipeline registers
    val instr_rdata_alu_i: Bool = Input(Bool()) // from IF-ID pipeline registers
    val instr_req_o: Bool = Output(Bool())
    val instr_first_cycle_id_o: Bool = Output(Bool())
    val instr_valid_clear_o: Bool = Output(Bool()) // kill instr in IF-ID reg
    val id_in_ready_o: Bool = Output(Bool()) // ID stage is ready for next instr

    // Jumps and branches
    val branch_decision_i: Bool = Input(Bool())

    // IF and ID stage signals
    val pc_set_o: Bool = Output(Bool())
    val pc_mux_o: pc_sel_e.Type = Output(pc_sel_e())

    val pc_id_i: UInt = Input(UInt(32.W))

    // Stalls
    val ex_valid_i: Bool = Input(Bool()) // EX stage has valid output ALU 输出(总是为 1)
    val lsu_resp_valid_i: Bool = Input(Bool()) // // LSU has valid output, or is done LSU 空闲标志
    // ALU
    val alu_operator_ex_o: alu_op_e.Type = Output(alu_op_e())
    val alu_operand_a_ex_o: UInt = Output(UInt(32.W))
    val alu_operand_b_ex_o: UInt = Output(UInt(32.W))

    //    // Multicycle Operation Stage Register
    //    input  logic [1:0]                imd_val_we_ex_i,
    //    input  logic [33:0]               imd_val_d_ex_i[2],
    //    output logic [33:0]               imd_val_q_ex_o[2],
    //
    //    // Branch target ALU
    //    output logic [31:0]               bt_a_operand_o,
    //    output logic [31:0]               bt_b_operand_o,
    //
    //    // MUL, DIV
    //    output logic                      mult_en_ex_o,
    //    output logic                      div_en_ex_o,
    //    output logic                      mult_sel_ex_o,
    //    output logic                      div_sel_ex_o,
    //    output ibex_pkg::md_op_e          multdiv_operator_ex_o,
    //    output logic  [1:0]               multdiv_signed_mode_ex_o,
    //    output logic [31:0]               multdiv_operand_a_ex_o,
    //    output logic [31:0]               multdiv_operand_b_ex_o,
    //    output logic                      multdiv_ready_id_o,
    //
    //    // CSR
    //    output logic                      csr_access_o,
    //    output ibex_pkg::csr_op_e         csr_op_o,
    //    output logic                      csr_op_en_o,
    //    output logic                      csr_save_if_o,
    //    output logic                      csr_save_id_o,
    //    output logic                      csr_save_wb_o,
    //    output logic                      csr_restore_mret_id_o,
    //    output logic                      csr_restore_dret_id_o,
    //    output logic                      csr_save_cause_o,
    //    output logic [31:0]               csr_mtval_o,
    //    input  ibex_pkg::priv_lvl_e       priv_mode_i,
    //    input  logic                      csr_mstatus_tw_i,
    //    input  logic                      illegal_csr_insn_i,
    //    input  logic                      data_ind_timing_i,

    // Interface to load store unit
    val lsu_req_o: Bool = Output(Bool())
    val lsu_we_o: Bool = Output(Bool())
    val lsu_wdata_o: UInt = Output(UInt(32.W))
    //    input  logic                      lsu_req_done_i, // Data req to LSU is complete and
    //    // instruction can move to writeback
    //    // (only relevant where writeback stage is
    //    // present)
    //
    //    input  logic                      lsu_addr_incr_req_i,
    //    input  logic [31:0]               lsu_addr_last_i,
    //
    //    // Interrupt signals
    //    input  logic                      csr_mstatus_mie_i,
    //    input  logic                      irq_pending_i,
    //    input  ibex_pkg::irqs_t           irqs_i,
    //    input  logic                      irq_nm_i,
    //    output logic                      nmi_mode_o,
    //
    //    input  logic                      lsu_load_err_i,
    //    input  logic                      lsu_store_err_i,
    //
    //    // Debug Signal
    //    output logic                      debug_mode_o,
    //    output ibex_pkg::dbg_cause_e      debug_cause_o,
    //    output logic                      debug_csr_save_o,
    //    input  logic                      debug_req_i,
    //    input  logic                      debug_single_step_i,
    //    input  logic                      debug_ebreakm_i,
    //    input  logic                      debug_ebreaku_i,
    //    input  logic                      trigger_match_i,
    //
    val result_ex_i: UInt = Input(UInt(32.W))


    // Register file read
    val rf_raddr_a_o: UInt = Output(UInt(5.W))
    val rf_rdata_a_i: UInt = Input(UInt(32.W))
    val rf_raddr_b_o: UInt = Output(UInt(5.W))
    val rf_rdata_b_i: UInt = Input(UInt(32.W))
    val rf_ren_a_o: Bool = Output(Bool())
    val rf_ren_b_o: Bool = Output(Bool())

    // Register file write (via writeback)
    val rf_waddr_id_o: UInt = Output(UInt(4.W))
    val rf_wdata_id_o: UInt = Input(UInt(32.W))
    val rf_we_id_o: Bool = Output(Bool())
    //    output logic                      rf_rd_a_wb_match_o,
    //    output logic                      rf_rd_b_wb_match_o,
    //
    //    // Register write information from writeback (for resolving data hazards)
    //    input  logic [4:0]                rf_waddr_wb_i,
    //    input  logic [31:0]               rf_wdata_fwd_wb_i,
    //    input  logic                      rf_write_wb_i,
    //
    //    output  logic                     en_wb_o,
    //    output  ibex_pkg::wb_instr_type_e instr_type_wb_o,
    //    output  logic                     instr_perf_count_id_o,
    //    input logic                       ready_wb_i,
    //    input logic                       outstanding_load_wb_i,
    //    input logic                       outstanding_store_wb_i,
    //
    //    // Performance Counters
    //    output logic                      perf_jump_o,    // executing a jump instr
    //    output logic                      perf_branch_o,  // executing a branch instr
    //    output logic                      perf_tbranch_o, // executing a taken branch instr
    //    output logic                      perf_dside_wait_o, // instruction in ID/EX is awaiting memory
    //    // access to finish before proceeding
    //    output logic                      perf_mul_wait_o,
    //    output logic                      perf_div_wait_o,
    val instr_id_done_o: Bool = Output(Bool())

  })

  val branch_in_dec: Bool = Wire(Bool())

  val branch_set: Bool = Wire(Bool())
  val branch_set_d: Bool = Wire(Bool())
  val jump_in_dec: Bool = Wire(Bool())
  val jump_set_dec: Bool = Wire(Bool())
  val jump_set: Bool = Wire(Bool())

  val instr_first_cycle: Bool = Wire(Bool())
  val instr_executing: Bool = Wire(Bool())
  val instr_done: Bool = Wire(Bool())
  val controller_run: Bool = Wire(Bool())
  val stall_mem: Bool = Wire(Bool())
  val stall_branch: Bool = Wire(Bool())
  val stall_jump: Bool = Wire(Bool())
  val stall_id: Bool = Wire(Bool())
  val multicycle_done: Bool = Wire(Bool())

  // Immediate decoding and sign extension
  val imm_i_type: UInt = Wire(UInt(32.W))
  val imm_s_type: UInt = Wire(UInt(32.W))
  val imm_b_type: UInt = Wire(UInt(32.W))
  val imm_u_type: UInt = Wire(UInt(32.W))
  val imm_j_type: UInt = Wire(UInt(32.W))
  val zimm_rs1_type: UInt = Wire(UInt(32.W))

  val imm_a: UInt = Wire(UInt(32.W)) // contains the immediate for operand b
  val imm_b: UInt = Wire(UInt(32.W)) // contains the immediate for operand b

  // Register file interface

  val rf_we_dec: Bool = Wire(Bool())
  val rf_we_raw: Bool = Wire(Bool())

  val rf_ren_a: Bool = Wire(Bool())
  val rf_ren_b: Bool = Wire(Bool())

  io.rf_ren_a_o := rf_ren_a
  io.rf_ren_b_o := rf_ren_b

  val rf_rdata_a_fwd: UInt = Wire(UInt(32.W))
  val rf_rdata_b_fwd: UInt = Wire(UInt(32.W))

  // ALU Control
  val alu_operator: alu_op_e.Type = Wire(alu_op_e())
  val alu_op_a_mux_sel: op_a_sel_e.Type = Wire(op_a_sel_e())
  val alu_op_a_mux_sel_dec: op_a_sel_e.Type = Wire(op_a_sel_e())
  val alu_op_b_mux_sel: op_b_sel_e.Type = Wire(op_b_sel_e())
  val alu_op_b_mux_sel_dec: op_b_sel_e.Type = Wire(op_b_sel_e())
  val alu_multicycle_dec: Bool = Wire(Bool())

  val imm_a_mux_sel: imm_a_sel_e.Type = Wire(imm_a_sel_e())
  val imm_b_mux_sel: imm_b_sel_e.Type = Wire(imm_b_sel_e())
  val imm_b_mux_sel_dec: imm_b_sel_e.Type = Wire(imm_b_sel_e())

  // Data Memory Control
  val lsu_we: Bool = Wire(Bool())
  val lsu_req: Bool = Wire(Bool())
  val lsu_req_dec: Bool = Wire(Bool())
  val data_req_allowed: Bool = Wire(Bool())

  val alu_operand_a: UInt = Wire(UInt(32.W))
  val alu_operand_b: UInt = Wire(UInt(32.W))

  /////////////
  // LSU Mux //
  /////////////

  // Misaligned loads/stores result in two aligned loads/stores, compute second address
  alu_op_a_mux_sel := alu_op_a_mux_sel_dec
  alu_op_b_mux_sel := alu_op_b_mux_sel_dec
  imm_b_mux_sel := imm_b_mux_sel_dec

  ///////////////////
  // Operand MUXES //
  ///////////////////

  // Main ALU immediate MUX for Operand A
  imm_a := 0.U

  // Main ALU MUX for Operand A
  alu_operand_a := MuxLookup(alu_op_a_mux_sel.asUInt(), io.pc_id_i.asUInt(), Array(
    op_a_sel_e.OP_A_REG_A.asUInt() -> rf_rdata_a_fwd.asUInt(),
    // todo: 不支持异常，不可能进入这种情况
    // op_a_sel_e.OP_A_FWD.asUInt() -> io.lsu_addr_last_i,
    op_a_sel_e.OP_A_CURRPC.asUInt() -> io.pc_id_i.asUInt(),
    op_a_sel_e.OP_A_IMM.asUInt() -> imm_a.asUInt()
  ))

  // Full main ALU immediate MUX for Operand B
  imm_b := MuxLookup(imm_b_mux_sel.asUInt(), 4.U(32.W), Array(
    imm_b_sel_e.IMM_B_I.asUInt() -> imm_i_type,
    imm_b_sel_e.IMM_B_S.asUInt() -> imm_s_type,
    imm_b_sel_e.IMM_B_B.asUInt() -> imm_b_type,
    imm_b_sel_e.IMM_B_U.asUInt() -> imm_u_type,
    imm_b_sel_e.IMM_B_J.asUInt() -> imm_j_type,
    imm_b_sel_e.IMM_B_INCR_PC.asUInt() -> 4.U(32.W),
    imm_b_sel_e.IMM_B_INCR_ADDR.asUInt() -> 4.U(32.W),
  ))

  // ALU MUX for Operand B
  alu_operand_b := Mux(alu_op_b_mux_sel === op_b_sel_e.OP_B_IMM, imm_b, rf_rdata_b_fwd)

  ///////////////////////
  // Register File MUX //
  ///////////////////////

  // Suppress register write if there is an illegal CSR access or instruction is not executing
  io.rf_we_id_o := rf_we_raw & instr_executing

  io.rf_wdata_id_o := io.result_ex_i

  /////////////
  // Decoder //
  /////////////

  val decoder: ibex_decoder = Module(new ibex_decoder)
  jump_set_dec := decoder.io.jump_set_o
  decoder.io.branch_taken_i := true.B // branch_taken_i 永远为true
  decoder.io.instr_first_cycle_i := instr_first_cycle
  decoder.io.instr_rdata_i := io.instr_rdata_i
  decoder.io.instr_rdata_alu_i := io.instr_rdata_alu_i

  imm_a_mux_sel := decoder.io.imm_a_mux_sel_o
  imm_b_mux_sel_dec := decoder.io.imm_b_mux_sel_o
  imm_i_type := decoder.io.imm_i_type_o
  imm_s_type := decoder.io.imm_s_type_o
  imm_b_type := decoder.io.imm_b_type_o
  imm_u_type := decoder.io.imm_u_type_o
  imm_j_type := decoder.io.imm_j_type_o

  //  rf_wdata_sel := decoder.io.rf_wdata_sel_o
  rf_we_dec := decoder.io.rf_we_o
  io.rf_raddr_a_o := decoder.io.rf_raddr_a_o
  io.rf_raddr_b_o := decoder.io.rf_raddr_b_o
  io.rf_waddr_id_o := decoder.io.rf_waddr_o
  rf_ren_a := decoder.io.rf_ren_a_o
  rf_ren_b := decoder.io.rf_ren_b_o

  alu_operator := decoder.io.alu_operator_o
  alu_op_a_mux_sel_dec := decoder.io.alu_op_a_mux_sel_o
  alu_op_b_mux_sel_dec := decoder.io.alu_op_b_mux_sel_o
  alu_multicycle_dec := decoder.io.alu_multicycle_o

  lsu_req_dec := decoder.io.data_req_o
  lsu_we := decoder.io.data_we_o
  // 本项目 LSU 仅支持 32 比特内存访问
  // lsu_type := decoder.io.data_type_o
  //  lsu_sign_ext := decoder.io.data_sign_extension_o

  jump_in_dec := decoder.io.jump_in_dec_o
  branch_in_dec := decoder.io.branch_in_dec_o

  ////////////////
  // Controller //
  ////////////////

  val controller: ibex_controller = Module(new ibex_controller)
  io.ctrl_busy_o <> controller.io.ctrl_busy_o
  io.instr_valid_clear_o <> controller.io.instr_valid_clear_o
  io.id_in_ready_o <> controller.io.id_in_ready_o
  controller_run := controller.io.controller_run_o
  io.instr_req_o <> controller.io.instr_req_o
  io.pc_set_o <> controller.io.pc_set_o
  io.pc_mux_o <> controller.io.pc_mux_o
  controller.io.branch_set_i := branch_set
  controller.io.jump_set_i := jump_set
  controller.io.stall_id_i := stall_id


  lsu_req := Mux(instr_executing, data_req_allowed & lsu_req_dec, false.B)

  io.lsu_req_o := lsu_req
  io.lsu_we_o := lsu_we
  io.lsu_wdata_o := rf_rdata_b_fwd

  io.alu_operator_ex_o := alu_operator
  io.alu_operand_a_ex_o := alu_operand_a
  io.alu_operand_b_ex_o := alu_operand_b

  ////////////////////////
  // Branch set control //
  ////////////////////////

  val reset_n: AsyncReset = (!reset.asBool).asAsyncReset

  val branch_set_q: Bool = Wire(Bool())
  branch_set_q := withReset(reset_n) {
    RegNext(branch_set_d, init = false.B)
  }

  // Branches always take two cycles in fixed time execution mode, with or without the branch
  // target ALU (to avoid a path from the branch decision into the branch target ALU operand
  // muxing).
  branch_set := branch_set_q


  ///////////////
  // ID-EX FSM //
  ///////////////

  val id_fsm_q: id_fsm_e.Type = Wire(id_fsm_e())
  val id_fsm_d: id_fsm_e.Type = Wire(id_fsm_e())

  id_fsm_q := withReset(reset_n) {
    RegNext(id_fsm_d, init = id_fsm_e.FIRST_CYCLE)
  }

  //  withReset(reset_n) {
  //    id_fsm_q := Mux(instr_executing, RegNext(id_fsm_d, init = id_fsm_e.FIRST_CYCLE), id_fsm_e.FIRST_CYCLE)
  //  }
  //  // Expect equal to below
  //  // always_ff @(posedge clk_i or negedge rst_ni) begin : id_pipeline_reg
  //  //   if (!rst_ni) begin
  //  //     id_fsm_q <= FIRST_CYCLE;
  //  //   end else if (instr_executing) begin
  //  //     id_fsm_q <= id_fsm_d;
  //  //   end
  //  // end

  // ID/EX stage can be in two states, FIRST_CYCLE and MULTI_CYCLE. An instruction enters
  // MULTI_CYCLE if it requires multiple cycles to complete regardless of stalls and other
  // considerations. An instruction may be held in FIRST_CYCLE if it's unable to begin executing
  // (this is controlled by instr_executing).

  {
    id_fsm_d := id_fsm_q;
    rf_we_raw := rf_we_dec;
    stall_jump := 0.U;
    stall_branch := 0.U;
    branch_set_d := 0.U;
    jump_set := 0.U;

    when(instr_executing) {
      when(id_fsm_q === id_fsm_e.FIRST_CYCLE) {
        when(lsu_req_dec === true.B) {
          id_fsm_d := id_fsm_e.MULTI_CYCLE
        }.elsewhen(branch_in_dec === true.B) {
          // cond branch operation
          // All branches take two cycles in fixed time execution mode, regardless of branch
          // condition.
          id_fsm_d := Mux(io.branch_decision_i, id_fsm_e.MULTI_CYCLE, id_fsm_e.FIRST_CYCLE)
          stall_branch := io.branch_decision_i
          branch_set_d := io.branch_decision_i
        }.elsewhen(jump_in_dec === true.B) {
          // uncond branch operation
          // BTALU means jumps only need one cycle
          id_fsm_d := id_fsm_e.MULTI_CYCLE
          stall_jump := true.B
          jump_set := jump_set_dec
        }.otherwise {
          id_fsm_d := id_fsm_e.FIRST_CYCLE
        }
      }.elsewhen(id_fsm_q === id_fsm_e.MULTI_CYCLE) {
        when(multicycle_done) {
          id_fsm_d := id_fsm_e.FIRST_CYCLE
        }.otherwise {
          stall_branch := branch_in_dec
          stall_jump := jump_in_dec
        }
      }.otherwise {
        id_fsm_d := id_fsm_e.FIRST_CYCLE
      }
    }

  }

  // Stall ID/EX stage for reason that relates to instruction in ID/EX
  stall_id := stall_mem | stall_jump | stall_branch
  instr_done := ~stall_id
  instr_first_cycle := io.instr_valid_i & (id_fsm_q === id_fsm_e.FIRST_CYCLE)
  io.instr_first_cycle_id_o := instr_first_cycle

  // 多周期指令完成：
  // - LSU 操作完成
  // - execute block 完成
  multicycle_done := Mux(lsu_req_dec, io.lsu_resp_valid_i, io.ex_valid_i)
  data_req_allowed := instr_first_cycle

  // Without Writeback Stage always stall the first cycle of a load/store.
  // Then stall until it is complete
  val tmp: Bool = Wire(Bool())
  tmp := ~io.lsu_resp_valid_i
  stall_mem := io.instr_valid_i & (lsu_req_dec & (tmp | instr_first_cycle))

  // Without writeback stage any valid instruction that hasn't seen an error will execute
  instr_executing := io.instr_valid_i & controller_run

  // No data forwarding without writeback stage so always take source register data direct from
  // register file
  rf_rdata_a_fwd := io.rf_rdata_a_i;
  rf_rdata_b_fwd := io.rf_rdata_b_i;

  io.instr_id_done_o := instr_done
}
