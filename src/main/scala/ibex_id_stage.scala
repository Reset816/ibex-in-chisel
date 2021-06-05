// 忽略 LSU
import Chisel.{Cat, is, switch}
import chisel3._
import chisel3.experimental.ChiselEnum
 class ibex_id_stage extends Module {
   val io = IO(new Bundle {
    // todo: 源源搞定时钟
    // val clk_i: Clock = Input(Clock())
    // val rst_ni: UInt = Input(UInt(1.W))
    val ctrl_busy_o = Input(Bool())

    val instr_valid_i = Input(Bool())
    val instr_rdata_i = Input(Bool())
    val instr_rdata_alu_i = Input(Bool())
    val instr_req_o = Output(Bool())
    val instr_first_cycle_id_o = Output(Bool())
    val instr_valid_clear_o = Output(Bool())
    val id_in_ready_o = Output(Bool())

    val branch_decision_i = Input(Bool())

    val pc_set_o = Output(Bool())
    val pc_mux_o = Output(pc_sel_e())

    val pc_id_i = Input(UInt(32.W))

    val ex_valid_i = Input(Bool()) // todo: ALU 输出

    val lsu_resp_valid_i = Input(Bool()) // todo: LSU 空闲标志
    val alu_operator_ex_o = Output(alu_op_e())
    val alu_operand_a_ex_o = Output(UInt(32.W))
    val alu_operand_b_ex_o = Output(UInt(32.W))

    val rf_raddr_a_o = Output(UInt(5.W))
    val rf_rdata_a_i = Input(UInt(32.W))
    val rf_raddr_b_o = Output(UInt(5.W))
    val rf_rdata_b_i = Input(UInt(32.W))
    val rf_ren_a_o = Output(Bool())
    val rf_ren_b_o = Output(Bool())

    val instr_id_done_o = Output(Bool())
   })

   val branch_in_dec = Wire(Bool())

   val branch_set = Wire(Bool())
   val branch_set_raw = Wire(Bool())
   val branch_set_raw_d = Wire(Bool())
   val branch_set_raw_q = Wire(Bool())

   val branch_jump_set_done_q = Wire(Bool())
   val branch_jump_set_done_d = Wire(Bool())

   val branch_taken = Wire(Bool())

   val jump_in_dec = Wire(Bool())

   val jump_set_dec = Wire(Bool())
   val jump_set = Wire(Bool())
   val jump_set_raw = Wire(Bool())

   val instr_first_cycle = Wire(Bool())
   val instr_executing = Wire(Bool())
   val instr_done = Wire(Bool())
   val controller_run = Wire(Bool())
   val stall_id_hz = Wire(Bool())
   val stall_mem = Wire(Bool())
   val stall_branch = Wire(Bool())
   val stall_jump = Wire(Bool())
   val stall_id = Wire(Bool())
   val multicycle_done = Wire(Bool())

   val imm_i_type = Wire(UInt(32.W))
   val imm_s_type = Wire(UInt(32.W))
   val imm_b_type = Wire(UInt(32.W))
   val imm_u_type = Wire(UInt(32.W))
   val imm_j_type = Wire(UInt(32.W))
   val zimm_rs1_type = Wire(UInt(32.W))

   val imm_a = Wire(UInt(32.W))
   val imm_b = Wire(UInt(32.W))

   val rf_we_dec = Wire(Bool())
   val rf_we_raw = Wire(Bool())

   val rf_ren_a = Wire(Bool())
   val rf_ren_b = Wire(Bool())

   val rf_ren_a_dec = Wire(Bool())
   val rf_ren_b_dec = Wire(Bool())

   rf_ren_a := instr_valid_i & rf_ren_a_dec
   rf_ren_b := instr_valid_i & rf_ren_b_dec

   rf_ren_a_o := rf_ren_a
   rf_ren_b_o := rf_ren_b

   val rf_rdata_a_fwd = Wire(UInt(32.W))
   val rf_rdata_b_fwd = Wire(UInt(32.W))

   val alu_operator = Wire(alu_op_e())
   val alu_op_a_mux_sel = Wire(op_a_sel_e())
   val alu_op_a_mux_sel_dec = Wire(op_a_sel_e())
   val alu_op_b_mux_sel = Wire(op_b_sel_e())
   val alu_op_b_mux_sel_dec = Wire(op_b_sel_e())
   val alu_multicycle_dec = Wire(Bool())
   val stall_alu = Wire(Bool())
   val imm_a_mux_sel = Wire(imm_a_sel_e())
   val imm_b_mux_sel = Wire(imm_b_sel_e())


   // TODO: Data Memory Control
   val lsu_we = Wire(Bool())
   val lsu_type = Wire(UInt(2.W))
   val lsu_sign_ext = Wire(Bool())
   val lsu_req = Wire(Bool())
   val lsu_req_dec = Wire(Bool())
   val data_req_allowed = Wire(Bool())

   val alu_operand_a = Wire(UInt(32.W))
   val alu_operand_b = Wire(UInt(32.W))

  // imm_a := Mux(ddsad,zimm,)
  imm_a := Mux(imm_a_mux_sel == imm_a_sel_e.IMM_A_Z, zimm_rs1_type, "0".U)

  // always_comb begin : alu_operand_a_mux
  //   unique case (alu_op_a_mux_sel)
  //     OP_A_REG_A:  alu_operand_a = rf_rdata_a_fwd;
  //     OP_A_FWD:    alu_operand_a = lsu_addr_last_i;
  //     OP_A_CURRPC: alu_operand_a = pc_id_i;
  //     OP_A_IMM:    alu_operand_a = imm_a;
  //     default:     alu_operand_a = pc_id_i;
  //   endcase
  // end
    // always_comb begin : immediate_b_mux
    //   unique case (imm_b_mux_sel)
    //     IMM_B_I:         imm_b = imm_i_type;
    //     IMM_B_S:         imm_b = imm_s_type;
    //     IMM_B_B:         imm_b = imm_b_type;
    //     IMM_B_U:         imm_b = imm_u_type;
    //     IMM_B_J:         imm_b = imm_j_type;
    //     IMM_B_INCR_PC:   imm_b = 32'h4
    //     IMM_B_INCR_ADDR: imm_b = 32'h4;
    //     default:         imm_b = 32'h4;
    //   endcase

    alu_operand_b := Mux(alu_op_b_mux_sel == imm_a_sel_e.OP_B_IMM, imm_b, rf_rdata_b_fwd)
    rf_we_id_o := rf_we_raw & instr_executing
    // ibex_decoder #(
    //     .RV32E           ( RV32E           ),
    //     .RV32M           ( RV32M           ),
    //     .RV32B           ( RV32B           ),
    //     .BranchTargetALU ( BranchTargetALU )
    // ) decoder_i (
    //     .clk_i                           ( clk_i                ),
    //     .rst_ni                          ( rst_ni               ),
    //
    //     // controller
    //     .illegal_insn_o                  ( illegal_insn_dec     ),
    //     .ebrk_insn_o                     ( ebrk_insn            ),
    //     .mret_insn_o                     ( mret_insn_dec        ),
    //     .dret_insn_o                     ( dret_insn_dec        ),
    //     .ecall_insn_o                    ( ecall_insn_dec       ),
    //     .wfi_insn_o                      ( wfi_insn_dec         ),
    //     .jump_set_o                      ( jump_set_dec         ),
    //     .branch_taken_i                  ( branch_taken /* 1 */         ),
    //     .icache_inval_o                  ( icache_inval_o       ),
    //
    //     // from IF-ID pipeline register
    //     .instr_first_cycle_i             ( instr_first_cycle    ),
    //     .instr_rdata_i                   ( instr_rdata_i        ),
    //     .instr_rdata_alu_i               ( instr_rdata_alu_i    ),
    //     .illegal_c_insn_i                ( illegal_c_insn_i     ),
    //
    //     // immediates
    //     .imm_a_mux_sel_o                 ( imm_a_mux_sel        ),
    //     .imm_b_mux_sel_o                 ( imm_b_mux_sel_dec    ),
    //     .bt_a_mux_sel_o                  ( bt_a_mux_sel         ),
    //     .bt_b_mux_sel_o                  ( bt_b_mux_sel         ),
    //
    //     .imm_i_type_o                    ( imm_i_type           ),
    //     .imm_s_type_o                    ( imm_s_type           ),
    //     .imm_b_type_o                    ( imm_b_type           ),
    //     .imm_u_type_o                    ( imm_u_type           ),
    //     .imm_j_type_o                    ( imm_j_type           ),
    //     .zimm_rs1_type_o                 ( zimm_rs1_type        ),
    //
    //     // register file
    //     .rf_wdata_sel_o                  ( rf_wdata_sel         ),
    //     .rf_we_o                         ( rf_we_dec            ),
    //
    //     .rf_raddr_a_o                    ( rf_raddr_a_o         ),
    //     .rf_raddr_b_o                    ( rf_raddr_b_o         ),
    //     .rf_waddr_o                      ( rf_waddr_id_o        ),
    //     .rf_ren_a_o                      ( rf_ren_a_dec         ),
    //     .rf_ren_b_o                      ( rf_ren_b_dec         ),
    //
    //     // ALU
    //     .alu_operator_o                  ( alu_operator         ),
    //     .alu_op_a_mux_sel_o              ( alu_op_a_mux_sel_dec ),
    //     .alu_op_b_mux_sel_o              ( alu_op_b_mux_sel_dec ),
    //     .alu_multicycle_o                ( alu_multicycle_dec   ),
    //
    //     // MULT & DIV
    //     .mult_en_o                       ( mult_en_dec          ),
    //     .div_en_o                        ( div_en_dec           ),
    //     .mult_sel_o                      ( mult_sel_ex_o        ),
    //     .div_sel_o                       ( div_sel_ex_o         ),
    //     .multdiv_operator_o              ( multdiv_operator     ),
    //     .multdiv_signed_mode_o           ( multdiv_signed_mode  ),
    //
    //     // CSRs
    //     .csr_access_o                    ( csr_access_o         ),
    //     .csr_op_o                        ( csr_op_o             ),
    //
    //     // LSU
    //     .data_req_o                      ( lsu_req_dec          ),
    //     .data_we_o                       ( lsu_we               ),
    //     .data_type_o                     ( lsu_type             ),
    //     .data_sign_extension_o           ( lsu_sign_ext         ),
    //
    //     // jump/branches
    //     .jump_in_dec_o                   ( jump_in_dec          ),
    //     .branch_in_dec_o                 ( branch_in_dec        )
    // );

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
    lsu_req_o := lsu_req
    lsu_we_o := lsu_we
    lsu_sign_ext_o := lsu_sign_ext
    lsu_wdata_o := rf_rdata_b_fwd

    alu_operator_ex_o := alu_operator
    alu_operand_a_ex_o := alu_operand_a
    alu_operand_b_ex_o := alu_operand_b

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

    branch_jump_set_done_d := (branch_set_raw | jump_set_raw | branch_jump_set_done_q) & ~instr_valid_clear_o
    // always_ff @(posedge clk_i or negedge rst_ni) begin
    //   if (!rst_ni) begin
    //     branch_jump_set_done_q <= 1'b0;
    //   end else begin
    //     branch_jump_set_done_q <= branch_jump_set_done_d;
    //   end
    // end
    jump_set := jump_set_raw & ~branch_set_raw_q
    branch_set := branch_set_raw & ~branch_jump_set_done_q

    branch_taken := "1".U // todo: decoder 需要这个信号，后续删除

  // typedef enum logic { FIRST_CYCLE, MULTI_CYCLE } id_fsm_e;
  // id_fsm_e id_fsm_q, id_fsm_d;
  //
  // always_ff @(posedge clk_i or negedge rst_ni) begin : id_pipeline_reg
  //   if (!rst_ni) begin
  //     id_fsm_q <= FIRST_CYCLE;
  //   end else if (instr_executing) begin
  //     id_fsm_q <= id_fsm_d;
  //   end
  // end
  //
  // // ID/EX stage can be in two states, FIRST_CYCLE and MULTI_CYCLE. An instruction enters
  // // MULTI_CYCLE if it requires multiple cycles to complete regardless of stalls and other
  // // considerations. An instruction may be held in FIRST_CYCLE if it's unable to begin executing
  // // (this is controlled by instr_executing).
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
  instr_first_cycle := instr_valid_i & (id_fsm_q == id_fsm_e.FIRST_CYCLE)
  instr_first_cycle_id_o := instr_first_cycle

  // 多周期指令完成：
  // - LSU 操作完成
  // - execute block 完成
  multicycle_done := Mux(lsu_req_dec, lsu_resp_valid_i, ex_valid_i)
  data_req_allowed := instr_first_cycle
  stall_mem := instr_valid_i & (lsu_req_dec & (~lsu_resp_valid_i | instr_first_cycle))
  instr_executing := instr_valid_i & controller_run

  // No data forwarding without writeback stage so always take source register data direct from
  // register file
  rf_rdata_a_fwd := rf_rdata_a_i;
  rf_rdata_b_fwd := rf_rdata_b_i;

  instr_id_done_o := instr_done
 }
