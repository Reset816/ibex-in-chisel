import chisel3._
import chisel3.util.{Cat, Fill, MuxLookup, is, switch}

class ibex_if_stage(
                     val DmHaltAddr: UInt = "h1A110800".U(32.W),
                     val DmExceptionAddr: UInt = "h1A110800".U(32.W),
                     val DummyInstructions: Bool = false.B,
                     val ICache: Bool = false.B,
                     val ICacheECC: Bool = false.B,
                     val PCIncrCheck: Bool = false.B,
                     val BranchPredictor: Bool = false.B,
                   ) extends Module {
  val io = IO(new Bundle {
    val rst_ni: Bool = IO(Input(Bool()))

    val boot_addr_i: UInt = IO(Input(UInt(32.W))) // also used for mtvec
    val req_i: Bool = IO(Input(Bool())) // instruction request control

    // instruction cache interface
    val instr_req_o: Bool = IO(Output(Bool()))
    val instr_addr_o: UInt = IO(Output(UInt(32.W)))
    val instr_gnt_i: Bool = IO(Input(Bool()))
    val instr_rvalid_i: Bool = IO(Input(Bool()))
    val instr_rdata_i: UInt = IO(Input(UInt(32.W)))
    val instr_err_i: Bool = IO(Input(Bool()))
    val instr_pmp_err_i: Bool = IO(Input(Bool()))

    // output of ID stage
    val instr_valid_id_o: Bool = IO(Output(Bool())) // instr in IF-ID is valid
    val instr_new_id_o: Bool = IO(Output(Bool())) // instr in IF-ID is new
    val instr_rdata_id_o: UInt = IO(Output(Reg(UInt(32.W)))) // instr for ID stage
    val instr_rdata_alu_id_o: UInt = IO(Output(UInt(32.W))) // replicated instr for ID stage
    // to reduce fan-out

    val instr_rdata_c_id_o: UInt = IO(Output(UInt(16.W))) // compressed instr for ID stage
    // (mtval), meaningful only if
    // instr_is_compressed_id_o = 1'b1

    val instr_is_compressed_id_o: Bool = IO(Output(Bool())) // compressed decoder thinks this
    // is a compressed instr

    val instr_bp_taken_o: Bool = IO(Output(Bool())) // instruction was predicted to be
    // a taken branch

    val instr_fetch_err_o: Bool = IO(Output(Bool())) // bus error on fetch
    val instr_fetch_err_plus2_o: Bool = IO(Output(Bool())) // bus error misaligned
    val illegal_c_insn_id_o: Bool = IO(Output(Bool())) // compressed decoder thinks this
    // is an invalid instr
    val dummy_instr_id_o: Bool = IO(Output(Bool())) // Instruction is a dummy
    val pc_if_o: UInt = IO(Output(UInt(32.W)))
    val pc_id_o: UInt = IO(Output(UInt(32.W)))

    // control signals
    val instr_valid_clear_i: Bool = IO(Input(Bool())) // clear instr valid bit in IF-ID
    val pc_set_i: Bool = IO(Input(Bool())) // set the PC to a new value
    val pc_set_spec_i: Bool = IO(Input(Bool()))
    val pc_mux_i: pc_sel_e.Type = IO(Input(pc_sel_e())) // selector for PC multiplexer
    val nt_branch_mispredict_i: Bool = IO(Input(Bool())) // Not-taken branch in ID/EX was
    // mispredicted (predicted taken)

    val exc_pc_mux_i: exc_pc_sel_e.Type = IO(Input(exc_pc_sel_e())) // selects ISR address
    val exc_cause: exc_cause_e.Type = IO(Input(exc_cause_e())) // selects ISR address for
    // vectorized interrupt lines

    val dummy_instr_en_i: Bool = IO(Input(Bool()))
    val dummy_instr_mask_i: UInt = IO(Input(UInt(3.W)))
    val dummy_instr_seed_en_i: Bool = IO(Input(Bool()))
    val dummy_instr_seed_i: UInt = IO(Input(UInt(32.W)))
    val icache_enable_i: Bool = IO(Input(Bool()))
    val icache_inval_i: Bool = IO(Input(Bool()))

    // jump and branch target
    val branch_target_ex_i: UInt = IO(Input(UInt(32.W))) // branch/jump target address

    // CSRs
    val csr_mepc_i: UInt = IO(Input(UInt(32.W))) // PC to restore after handling
    // the interrupt/exception
    val csr_depc_i: UInt = IO(Input(UInt(32.W))) // PC to restore after handling
    // the debug request
    val csr_mtvec_i: UInt = IO(Input(UInt(32.W))) // base PC to jump to on exception
    val csr_mtvec_init_o: Bool = IO(Output(Bool())) // tell CS regfile to init mtvec
    // pipeline stall
    val id_in_ready_i: Bool = IO(Input(Bool())) // ID stage is ready for new instr
    // misc signals
    val pc_mismatch_alert_o: Bool = IO(Output(Bool()))
    val if_busy_o: Bool = IO(Output(Bool()))
  })
  val instr_valid_id_d: Bool = Wire(Bool())
  val instr_valid_id_q: Bool = Wire(Bool())
  val instr_new_id_d: Bool = Wire(Bool())
  val instr_new_id_q: Bool = Wire(Bool())

  // prefetch buffer related signals
  val prefetch_busy: Bool = Wire(Bool())
  val branch_req: Bool = Wire(Bool())
  val branch_spec: Bool = Wire(Bool())
  val predicted_branch: Bool = Wire(Bool())
  val fetch_addr_n: UInt = Wire(UInt(32.W))
  val unused_fetch_addr_n0: Bool = Wire(Bool())

  val fetch_valid: Bool = Wire(Bool())
  val fetch_ready: Bool = Wire(Bool())
  val fetch_rdata: UInt = Wire(UInt(32.W))
  val fetch_addr: UInt = Wire(UInt(32.W))
  val fetch_err: Bool = Wire(Bool())
  val fetch_err_plus2: Bool = Wire(Bool())

  val if_instr_valid: Bool = Wire(Bool())
  val if_instr_rdata: UInt = Wire(UInt(32.W))
  val if_instr_addr: UInt = Wire(UInt(32.W))
  val if_instr_err: Bool = Wire(Bool())

  val exc_pc: UInt = Wire(UInt(32.W))

  val irq_id: UInt = Wire(UInt(6.W))
  val unused_irq_bit: Bool = Wire(Bool())

  val if_id_pipe_reg_we: Bool = Wire(Bool()) // IF-ID pipeline reg write enable

  // Dummy instruction signals
  val stall_dummy_instr: Bool = Wire(Bool())
  val instr_out: UInt = Wire(UInt(32.W))
  val instr_is_compressed_out: Bool = Wire(Bool())
  val illegal_c_instr_out: Bool = Wire(Bool())
  val instr_err_out: Bool = Wire(Bool())

  val predict_branch_taken: Bool = Wire(Bool())
  val predict_branch_pc: UInt = Wire(UInt(32.W))

  val pc_mux_internal: pc_sel_e.Type = Wire(pc_sel_e())

  val unused_boot_addr: UInt = Wire(UInt(8.W))
  val unused_csr_mtvec: UInt = Wire(UInt(8.W))

  unused_boot_addr := io.boot_addr_i(7, 0).asUInt
  unused_csr_mtvec := io.csr_mtvec_i(7, 0).asUInt

  // extract interrupt ID from exception cause
  irq_id := io.exc_cause.asUInt()
  unused_irq_bit := irq_id(5) // MSB distinguishes interrupts from exceptions

  // exception PC selection mux
  exc_pc := MuxLookup(io.exc_pc_mux_i.asUInt(), Cat(io.csr_mtvec_i(31, 8), 0.U(8.W)), Array(
    exc_pc_sel_e.EXC_PC_EXC.asUInt() -> Cat(io.csr_mtvec_i(31, 8), 0.U(8.W)),
    exc_pc_sel_e.EXC_PC_IRQ.asUInt() -> Cat(io.csr_mtvec_i(31, 8), 0.U(1.W), irq_id(4, 0), 0.U(2)),
    exc_pc_sel_e.EXC_PC_DBD.asUInt() -> DmHaltAddr,
    exc_pc_sel_e.EXC_PC_DBG_EXC.asUInt() -> DmExceptionAddr
  ))

  // The Branch predictor can provide a new PC which is internal to if_stage. Only override the mux
  // select to choose this if the core isn't already trying to set a PC.
  pc_mux_internal := Mux((BranchPredictor && predict_branch_taken && !io.pc_set_i), pc_sel_e.PC_BOOT, pc_sel_e.PC_BOOT)

  // fetch address selection mux
  fetch_addr_n := MuxLookup(pc_mux_internal.asUInt(), Cat(io.boot_addr_i(31, 8), "h80".U(8.W)), Array(
    pc_sel_e.PC_BOOT.asUInt() -> Cat(io.boot_addr_i(31, 8), "h80".U(8.W)),
    pc_sel_e.PC_JUMP.asUInt() -> io.branch_target_ex_i,
    pc_sel_e.PC_EXC.asUInt() -> exc_pc, // set PC to exception handler
    pc_sel_e.PC_ERET.asUInt() -> io.csr_mepc_i, // restore PC when returning from EXC
    pc_sel_e.PC_DRET.asUInt() -> io.csr_mepc_i,
    // Without branch predictor will never get pc_mux_internal == PC_BP. We still handle no branch
    // predictor case here to ensure redundant mux logic isn't synthesised.
    pc_sel_e.PC_BP.asUInt() -> Mux(BranchPredictor, predict_branch_pc, Cat(io.boot_addr_i(31, 8), "h80".U(8.W)))
  ))

  // tell CS register file to initialize mtvec on boot
  io.csr_mtvec_init_o := (io.pc_mux_i === pc_sel_e.PC_BOOT) & io.pc_set_i

  // No ICache

  unused_fetch_addr_n0 := fetch_addr_n(0)
  branch_req := io.pc_set_i | predict_branch_taken
  branch_spec := io.pc_set_spec_i | predict_branch_taken
  io.pc_if_o := if_instr_addr
  io.if_busy_o := prefetch_busy

  // No Compressed Decoder

  // No Dummy instruction

  // The ID stage becomes valid as soon as any instruction is registered in the ID stage flops.
  // Note that the current instruction is squashed by the incoming pc_set_i signal.
  // Valid is held until it is explicitly cleared (due to an instruction completing or an exception)

  val tmp1: Bool = Wire(Bool())
  tmp1 := if_instr_valid & io.id_in_ready_i & ~io.pc_set_i
  val tmp2: Bool = Wire(Bool())
  tmp2 := instr_valid_id_q & ~io.instr_valid_clear_i
  //    instr_valid_id_d := (if_instr_valid & io.id_in_ready_i & ~io.pc_set_i) | (instr_valid_id_q & ~io.instr_valid_clear_i)
  instr_valid_id_d := tmp1 | tmp2
  instr_new_id_d := if_instr_valid & io.id_in_ready_i

  //todo
  //  always_ff @(posedge clk_i or negedge rst_ni) begin
  //  if (!rst_ni) begin
  //  instr_valid_id_q <= 1'b0;
  //  instr_new_id_q   <= 1'b0;
  //  end else begin
  //  instr_valid_id_q <= instr_valid_id_d;
  //  instr_new_id_q   <= instr_new_id_d;
  //  end
  //  end

  io.instr_valid_id_o := instr_valid_id_q
  // Signal when a new instruction enters the ID stage (only used for RVFI signalling).
  io.instr_new_id_o := instr_new_id_q

  // IF-ID pipeline registers, frozen when the ID stage is stalled
  if_id_pipe_reg_we := instr_new_id_d

  when(if_id_pipe_reg_we) { // Reference: https://stackoverflow.com/questions/62388061/how-to-define-output-reg-in-chisel-properly
    io.instr_rdata_id_o := RegNext(instr_out)
    // To reduce fan-out and help timing from the instr_rdata_id flops they are replicated.
    io.instr_rdata_alu_id_o := RegNext(instr_out)
    io.instr_fetch_err_o := RegNext(instr_err_out)
    io.instr_fetch_err_plus2_o := RegNext(fetch_err_plus2)
    io.instr_rdata_c_id_o := RegNext(if_instr_rdata(15, 0))
    io.instr_is_compressed_id_o := RegNext(instr_is_compressed_out)
    io.illegal_c_insn_id_o := RegNext(illegal_c_instr_out)
    io.pc_id_o := RegNext(io.pc_if_o)
  }

  // Check for expected increments of the PC when security hardening enabled
  when(PCIncrCheck) {
    val prev_instr_addr_incr = Wire(UInt(32.W))
    val prev_instr_seq_q = Wire(Bool())
    val prev_instr_seq_d = Wire(Bool())

    // Do not check for sequential increase after a branch, jump, exception, interrupt or debug
    // request, all of which will set branch_req. Also do not check after reset or for dummys.

    val tmp3: Bool = Wire(Bool())
    tmp3 := (prev_instr_seq_q | instr_new_id_d) & ~branch_req
    //    prev_instr_seq_d := (prev_instr_seq_q | instr_new_id_d) & ~branch_req & ~stall_dummy_instr
    prev_instr_seq_d := tmp3 & ~stall_dummy_instr

    //todo
    //    always_ff @(posedge clk_i or negedge rst_ni) begin
    //    if (!rst_ni) begin
    //    prev_instr_seq_q <= 1'b0;
    //    end else begin
    //    prev_instr_seq_q <= prev_instr_seq_d;
    //    end
    //    end

    prev_instr_addr_incr := io.pc_id_o + Mux((io.instr_is_compressed_id_o && !io.instr_fetch_err_o), 2.U(32.W), 4.U(32.W))

    // Check that the address equals the previous address +2/+4
    io.pc_mismatch_alert_o := prev_instr_seq_q & (io.pc_if_o =/= prev_instr_addr_incr)

  }.otherwise {
    io.pc_mismatch_alert_o := 0.U
  }

  // No BranchPredictor
  io.instr_bp_taken_o := false.B
  predict_branch_taken := false.B
  predicted_branch := false.B
  predict_branch_pc := "b0".U(32.W)
  if_instr_valid := fetch_valid
  if_instr_rdata := fetch_rdata
  if_instr_addr := fetch_addr
  if_instr_err := fetch_err
  fetch_ready := io.id_in_ready_i & ~stall_dummy_instr

}

