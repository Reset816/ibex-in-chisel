// 忽略 LSU

import Chisel.{Cat, MuxLookup, is, switch}
import chisel3._
import chisel3.experimental.ChiselEnum

class ibex_id_stage extends Module {
  val io = IO(new Bundle {
    val ctrl_busy_o: Bool = Input(Bool())

    val instr_valid_i: Bool = Input(Bool())
    val instr_rdata_i: Bool = Input(Bool())
    val instr_rdata_alu_i: Bool = Input(Bool())
    val instr_req_o: Bool = Output(Bool())
    val instr_first_cycle_id_o: Bool = Output(Bool())
    val instr_valid_clear_o: Bool = Output(Bool())
    val id_in_ready_o: Bool = Output(Bool())

    val branch_decision_i: Bool = Input(Bool())

    val pc_set_o: Bool = Output(Bool())
    val pc_mux_o: pc_sel_e.Type = Output(pc_sel_e())

    val pc_id_i: UInt = Input(UInt(32.W))

    val ex_valid_i: Bool = Input(Bool()) // todo: ALU 输出(总是为 1)

    val lsu_resp_valid_i: Bool = Input(Bool()) // todo: LSU 空闲标志
    val lsu_req_o: Bool = Output(Bool())
    val lsu_we_o: Bool = Output(Bool())
    val lsu_wdata_o: UInt = Output(UInt(32.W))

    val alu_operator_ex_o: alu_op_e.Type = Output(alu_op_e())
    val alu_operand_a_ex_o: UInt = Output(UInt(32.W))
    val alu_operand_b_ex_o: UInt = Output(UInt(32.W))

    val rf_raddr_a_o: UInt = Output(UInt(5.W))
    val rf_rdata_a_i: UInt = Input(UInt(32.W))
    val rf_raddr_b_o: UInt = Output(UInt(5.W))
    val rf_rdata_b_i: UInt = Input(UInt(32.W))
    val rf_ren_a_o: Bool = Output(Bool())
    val rf_ren_b_o: Bool = Output(Bool())

    val rf_waddr_id_o: UInt = Output(UInt(4.W))
    val rf_we_id_o: Bool = Output(Bool())


    val instr_id_done_o: Bool = Output(Bool())


  })

  val branch_in_dec: Bool = Wire(Bool())

  val branch_set: Bool = Wire(Bool())
  val branch_set_raw: Bool = Wire(Bool())
  val branch_set_raw_d: Bool = Wire(Bool())
  val branch_set_raw_q: Bool = Wire(Bool())

  val branch_jump_set_done_q: Bool = Wire(Bool())
  val branch_jump_set_done_d: Bool = Wire(Bool())

  val branch_taken: Bool = Wire(Bool())

  val jump_in_dec: Bool = Wire(Bool())

  val jump_set_dec: Bool = Wire(Bool())
  val jump_set: Bool = Wire(Bool())
  val jump_set_raw: Bool = Wire(Bool())

  val instr_first_cycle: Bool = Wire(Bool())
  val instr_executing: Bool = Wire(Bool())
  val instr_done: Bool = Wire(Bool())
  val controller_run: Bool = Wire(Bool())
  val stall_id_hz: Bool = Wire(Bool())
  val stall_mem: Bool = Wire(Bool())
  val stall_branch: Bool = Wire(Bool())
  val stall_jump: Bool = Wire(Bool())
  val stall_id: Bool = Wire(Bool())
  val multicycle_done: Bool = Wire(Bool())

  val imm_i_type: UInt = Wire(UInt(32.W))
  val imm_s_type: UInt = Wire(UInt(32.W))
  val imm_b_type: UInt = Wire(UInt(32.W))
  val imm_u_type: UInt = Wire(UInt(32.W))
  val imm_j_type: UInt = Wire(UInt(32.W))
  val zimm_rs1_type: UInt = Wire(UInt(32.W))

  val imm_a: UInt = Wire(UInt(32.W))
  val imm_b: UInt = Wire(UInt(32.W))

  // Register file interface

  val rf_wdata_sel: rf_wd_sel_e.Type = Wire(rf_wd_sel_e())
  val rf_we_dec: Bool = Wire(Bool())
  val rf_we_raw: Bool = Wire(Bool())

  val rf_ren_a: Bool = Wire(Bool())
  val rf_ren_b: Bool = Wire(Bool())

  val rf_ren_a_dec: Bool = Wire(Bool())
  val rf_ren_b_dec: Bool = Wire(Bool())

  rf_ren_a := io.instr_valid_i & rf_ren_a_dec
  rf_ren_b := io.instr_valid_i & rf_ren_b_dec

  io.rf_ren_a_o := rf_ren_a
  io.rf_ren_b_o := rf_ren_b

  val rf_rdata_a_fwd: UInt = Wire(UInt(32.W))
  val rf_rdata_b_fwd: UInt = Wire(UInt(32.W))

  val alu_operator: alu_op_e.Type = Wire(alu_op_e())
  val alu_op_a_mux_sel: op_a_sel_e.Type = Wire(op_a_sel_e())
  val alu_op_a_mux_sel_dec: op_a_sel_e.Type = Wire(op_a_sel_e())
  val alu_op_b_mux_sel: op_b_sel_e.Type = Wire(op_b_sel_e())
  val alu_op_b_mux_sel_dec: op_b_sel_e.Type = Wire(op_b_sel_e())
  val alu_multicycle_dec: Bool = Wire(Bool())
  val stall_alu: Bool = Wire(Bool())
  val imm_a_mux_sel: imm_a_sel_e.Type = Wire(imm_a_sel_e())
  val imm_b_mux_sel: imm_b_sel_e.Type = Wire(imm_b_sel_e())
  val imm_b_mux_sel_dec: imm_b_sel_e.Type = Wire(imm_b_sel_e())


  // TODO: Data Memory Control
  val lsu_we: Bool = Wire(Bool())
  val lsu_req: Bool = Wire(Bool())
  val lsu_req_dec: Bool = Wire(Bool())
  val data_req_allowed: Bool = Wire(Bool())

  val alu_operand_a: UInt = Wire(UInt(32.W))
  val alu_operand_b: UInt = Wire(UInt(32.W))

  imm_a := Mux(imm_a_mux_sel === imm_a_sel_e.IMM_A_Z, zimm_rs1_type, "0".U)

  alu_operand_a := MuxLookup(alu_op_a_mux_sel, io.pc_id_i, Array(
    op_a_sel_e.OP_A_REG_A.asUInt() -> rf_rdata_a_fwd,
    // todo: 不支持异常，不可能进入这种情况
    // op_a_sel_e.OP_A_FWD.asUInt() -> io.lsu_addr_last_i,
    op_a_sel_e.OP_A_CURRPC.asUInt() -> io.pc_id_i,
    op_a_sel_e.OP_A_IMM.asUInt() -> imm_a,
  ))

  imm_b := MuxLookup(imm_b_mux_sel, 4.U(32.W), Array(
    imm_b_sel_e.IMM_B_I.asUInt() -> imm_i_type,
    imm_b_sel_e.IMM_B_S.asUInt() -> imm_s_type,
    imm_b_sel_e.IMM_B_B.asUInt() -> imm_b_type,
    imm_b_sel_e.IMM_B_U.asUInt() -> imm_u_type,
    imm_b_sel_e.IMM_B_J.asUInt() -> imm_j_type,
    imm_b_sel_e.IMM_B_INCR_PC.asUInt() -> 4.U(32.W),
    imm_b_sel_e.IMM_B_INCR_ADDR.asUInt() -> 4.U(32.W),
  ))

  alu_operand_b := Mux(alu_op_b_mux_sel === op_b_sel_e.OP_B_IMM, imm_b, rf_rdata_b_fwd)
  io.rf_we_id_o := rf_we_raw & instr_executing

  val decoder: ibex_decoder = Module(new ibex_decoder)
  // todo: clk_i & rst_ni
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
  zimm_rs1_type := decoder.io.zimm_rs1_type_o

  rf_wdata_sel := decoder.io.rf_wdata_sel_o
  rf_we_dec := decoder.io.rf_we_o
  io.rf_raddr_a_o := decoder.io.rf_raddr_a_o
  io.rf_raddr_b_o := decoder.io.rf_raddr_b_o
  io.rf_waddr_id_o := decoder.io.rf_waddr_o
  rf_ren_a_dec := decoder.io.rf_ren_a_o
  rf_ren_b_dec := decoder.io.rf_ren_b_o

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


  val controller: ibex_controller = Module(new ibex_controller)
  // todo: clk_i & rst_ni
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

  // ibex_controller #(
  //   .WritebackStage  ( WritebackStage  ),
  //   .BranchPredictor ( BranchPredictor )
  // ) controller_i (
  //     .clk_i                          ( clk_i                   ),
  //     .rst_ni                         ( rst_ni                  ),
  //
  //     .ctrl_busy_o                    ( ctrl_busy_o             ),
  //
  //     // decoder related signals
  //     .illegal_insn_i                 ( illegal_insn_o          ),
  //     .ecall_insn_i                   ( ecall_insn_dec          ),
  //     .mret_insn_i                    ( mret_insn_dec           ),
  //     .dret_insn_i                    ( dret_insn_dec           ),
  //     .wfi_insn_i                     ( wfi_insn_dec            ),
  //     .ebrk_insn_i                    ( ebrk_insn               ),
  //     .csr_pipe_flush_i               ( csr_pipe_flush          ),
  //
  //     // from IF-ID pipeline
  //     .instr_valid_i                  ( instr_valid_i           ),
  //     .instr_i                        ( instr_rdata_i           ),
  //     .instr_compressed_i             ( instr_rdata_c_i         ),
  //     .instr_is_compressed_i          ( instr_is_compressed_i   ),
  //     .instr_bp_taken_i               ( instr_bp_taken_i /* 0 */        ),
  //     .instr_fetch_err_i              ( instr_fetch_err_i       ),
  //     .instr_fetch_err_plus2_i        ( instr_fetch_err_plus2_i ),
  //     .pc_id_i                        ( pc_id_i                 ),
  //
  //     // to IF-ID pipeline
  //     .instr_valid_clear_o            ( instr_valid_clear_o     ),
  //     .id_in_ready_o                  ( id_in_ready_o           ),
  //     .controller_run_o               ( controller_run          ),
  //
  //     // to prefetcher
  //     .instr_req_o                    ( instr_req_o             ),
  //     .pc_set_o                       ( pc_set_o                ),
  //     .pc_set_spec_o                  ( pc_set_spec_o           ),
  //     .pc_mux_o                       ( pc_mux_o                ),
  //     .nt_branch_mispredict_o         ( nt_branch_mispredict_o  ),
  //     .exc_pc_mux_o                   ( exc_pc_mux_o            ),
  //     .exc_cause_o                    ( exc_cause_o             ),
  //
  //     // LSU
  //     .lsu_addr_last_i                ( lsu_addr_last_i         ),
  //     .load_err_i                     ( lsu_load_err_i          ),
  //     .store_err_i                    ( lsu_store_err_i         ),
  //     .wb_exception_o                 ( wb_exception            ),
  //
  //     // jump/branch control
  //     .branch_set_i                   ( branch_set              ),
  //     .branch_set_spec_i              ( branch_set_spec         ),
  //     .branch_not_set_i               ( branch_not_set          ),
  //     .jump_set_i                     ( jump_set                ),
  //
  //     // interrupt signals
  //     .csr_mstatus_mie_i              ( csr_mstatus_mie_i       ),
  //     .irq_pending_i                  ( irq_pending_i           ),
  //     .irqs_i                         ( irqs_i                  ),
  //     .irq_nm_i                       ( irq_nm_i                ),
  //     .nmi_mode_o                     ( nmi_mode_o              ),
  //
  //     // CSR Controller Signals
  //     .csr_save_if_o                  ( csr_save_if_o           ),
  //     .csr_save_id_o                  ( csr_save_id_o           ),
  //     .csr_save_wb_o                  ( csr_save_wb_o           ),
  //     .csr_restore_mret_id_o          ( csr_restore_mret_id_o   ),
  //     .csr_restore_dret_id_o          ( csr_restore_dret_id_o   ),
  //     .csr_save_cause_o               ( csr_save_cause_o        ),
  //     .csr_mtval_o                    ( csr_mtval_o             ),
  //     .priv_mode_i                    ( priv_mode_i             ),
  //     .csr_mstatus_tw_i               ( csr_mstatus_tw_i        ),
  //
  //     // Debug Signal
  //     .debug_mode_o                   ( debug_mode_o            ),
  //     .debug_cause_o                  ( debug_cause_o           ),
  //     .debug_csr_save_o               ( debug_csr_save_o        ),
  //     .debug_req_i                    ( debug_req_i             ),
  //     .debug_single_step_i            ( debug_single_step_i     ),
  //     .debug_ebreakm_i                ( debug_ebreakm_i         ),
  //     .debug_ebreaku_i                ( debug_ebreaku_i         ),
  //     .trigger_match_i                ( trigger_match_i         ),
  //
  //     .stall_id_i                     ( stall_id                ),
  //     .stall_wb_i                     ( stall_wb /* 0 */                ),
  //     .flush_id_o                     ( flush_id                ),
  //     .ready_wb_i                     ( ready_wb_i              ),
  //
  //     // Performance Counters
  //     .perf_jump_o                    ( perf_jump_o             ),
  //     .perf_tbranch_o                 ( perf_tbranch_o          )
  // );

  // TODO: LSU
  lsu_req := Mux(instr_executing, data_req_allowed & lsu_req_dec, "1".U)
  io.lsu_req_o := lsu_req
  io.lsu_we_o := lsu_we
  io.lsu_wdata_o := rf_rdata_b_fwd

  io.alu_operator_ex_o := alu_operator
  io.alu_operand_a_ex_o := alu_operand_a
  io.alu_operand_b_ex_o := alu_operand_b

  // logic branch_set_raw_q;
  //
  // always_ff @(posedge clk_i or negedge rst_ni) begin // rst_ni(reset PC)
  //   if (!rst_ni) begin
  //     branch_set_raw_q <= 1'b0;
  //   end else begin
  //     branch_set_raw_q <= branch_set_raw_d;
  //   end
  // end
  //
  // assign branch_set_raw      = /* (BranchTargetALU && !data_ind_timing_i) ? branch_set_raw_d : */

  branch_jump_set_done_d := (branch_set_raw | jump_set_raw | branch_jump_set_done_q) & ~io.instr_valid_clear_o
  // always_ff @(posedge clk_i or negedge rst_ni) begin
  //   if (!rst_ni) begin
  //     branch_jump_set_done_q <= 1'b0;
  //   end else begin
  //     branch_jump_set_done_q <= branch_jump_set_done_d;
  //   end
  // end
  jump_set := jump_set_raw & ~branch_set_raw_q
  branch_set := branch_set_raw & ~branch_jump_set_done_q

  object id_fsm_e extends ChiselEnum {
    val
    FIRST_CYCLE,
    MULTI_CYCLE
    = Value
  }

  val id_fsm_q: id_fsm_e.Type = id_fsm_e()
  val id_fsm_d: id_fsm_e.Type = id_fsm_e()

  val reset_n: AsyncReset = (!reset.asBool).asAsyncReset
  id_fsm_q := withReset(reset_n) {
    RegNext(id_fsm_d, init = ctrl_fsm_e.RESET)
  }

  withReset(reset_n) {
    id_fsm_q := Mux(instr_executing, RegNext(id_fsm_d, init = id_fsm_e.FIRST_CYCLE), id_fsm_e.FIRST_CYCLE)
  }
  // Expect equal to below
  // always_ff @(posedge clk_i or negedge rst_ni) begin : id_pipeline_reg
  //   if (!rst_ni) begin
  //     id_fsm_q <= FIRST_CYCLE;
  //   end else if (instr_executing) begin
  //     id_fsm_q <= id_fsm_d;
  //   end
  // end

  // ID/EX stage can be in two states, FIRST_CYCLE and MULTI_CYCLE. An instruction enters
  // MULTI_CYCLE if it requires multiple cycles to complete regardless of stalls and other
  // considerations. An instruction may be held in FIRST_CYCLE if it's unable to begin executing
  // (this is controlled by instr_executing).
  //
  // always_comb begin
  //   id_fsm_d                = id_fsm_q;
  //   rf_we_raw               = rf_we_dec;
  //   stall_multdiv           = 1'b0;
  //   stall_jump              = 1'b0;
  //   stall_branch            = 1'b0;
  //   stall_alu               = 1'b0;
  //   branch_set_raw_d        = 1'b0;
  //   branch_spec             = 1'b0;
  //   branch_not_set          = 1'b0;
  //   jump_set_raw            = 1'b0;
  //   perf_branch_o           = 1'b0;
  //
  //   if (instr_executing_spec /* instr_executing */) begin
  //     unique case (id_fsm_q)
  //       FIRST_CYCLE: begin
  //         unique case (1'b1)
  //           lsu_req_dec: begin // LSU 请求进入 MULTI_CYCLE
  //               id_fsm_d    = MULTI_CYCLE;
  //           end
  //           branch_in_dec: begin // branch 有条件跳转
  //             // cond branch operation
  //             // All branches take two cycles in fixed time execution mode, regardless of branch
  //             // condition.
  //             id_fsm_d         =  branch_decision_i ?
  //                                    MULTI_CYCLE : FIRST_CYCLE;
  //             stall_branch     = (~BranchTargetALU /* 1 */ & branch_decision_i) | data_ind_timing_i;
  //             branch_set_raw_d = (branch_decision_i | data_ind_timing_i);
  //
  //           end
  //           jump_in_dec: begin // jump 进入 MULTI_CYCLE
  //             // uncond branch operation
  //             // BTALU means jumps only need one cycle
  //             id_fsm_d      = MULTI_CYCLE;
  //             stall_jump    = ~BranchTargetALU /* 1 */;
  //             jump_set_raw  = jump_set_dec /* 执行到这里必为 1 */;
  //           end
  //           alu_multicycle_dec: begin
  //             stall_alu     = 1'b1;
  //             id_fsm_d      = MULTI_CYCLE;
  //             rf_we_raw     = 1'b0;
  //           end
  //           default: begin
  //             id_fsm_d      = FIRST_CYCLE;
  //           end
  //         endcase
  //       end
  //
  //       MULTI_CYCLE: begin
  //         if (multicycle_done) begin
  //           id_fsm_d        = FIRST_CYCLE;
  //         end else begin
  //           stall_branch    = branch_in_dec;
  //           stall_jump      = jump_in_dec;
  //         end
  //       end
  //
  //       default: begin
  //         id_fsm_d          = FIRST_CYCLE;
  //       end
  //     endcase
  //   end
  // end
  stall_id := stall_mem | stall_jump | stall_branch | stall_alu
  instr_done := ~stall_id
  instr_first_cycle := io.instr_valid_i & (id_fsm_q == id_fsm_e.FIRST_CYCLE)
  io.instr_first_cycle_id_o := instr_first_cycle

  // 多周期指令完成：
  // - LSU 操作完成
  // - execute block 完成
  multicycle_done := Mux(lsu_req_dec, io.lsu_resp_valid_i, io.ex_valid_i)
  data_req_allowed := instr_first_cycle
  stall_mem := io.instr_valid_i & (lsu_req_dec & (~io.lsu_resp_valid_i | instr_first_cycle))
  instr_executing := io.instr_valid_i & controller_run

  // No data forwarding without writeback stage so always take source register data direct from
  // register file
  rf_rdata_a_fwd := io.rf_rdata_a_i;
  rf_rdata_b_fwd := io.rf_rdata_b_i;

  io.instr_id_done_o := instr_done
}
