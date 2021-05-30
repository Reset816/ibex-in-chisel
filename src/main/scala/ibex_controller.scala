import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{is, switch}

class ibex_controller extends Module {
  val io = IO(new Bundle {

    val rst_ni: Bool = IO(Input(Bool()))
    val ctrl_busy_o: Bool = IO(Output(Bool())) // core is busy processing instrs

    // instr from IF-ID pipeline stage
    val instr_valid_i: Bool = IO(Input(Bool())) // instr is valid

    val instr_i: UInt = IO(Input(UInt(32.W))) // uncompressed instr data for mtval

    val pc_id_i: UInt = IO(Input(UInt(32.W))) // instr address

    // to IF-ID pipeline stage
    val instr_valid_clear_o: Bool = IO(Output(Bool())) // kill instr in IF-ID reg

    val id_in_ready_o: Bool = IO(Output(Bool())) // ID stage is ready for new instr

    val controller_run_o: Bool = IO(Output(Bool())) // Controller is in standard instruction
    // run mode

    // to prefetcher
    val instr_req_o: Bool = IO(Output(Bool())) // start fetching instructions

    val pc_set_o: Bool = IO(Output(Bool())) // jump to address set by pc_mux

    val pc_mux_o: pc_sel_e.Type = IO(Output(pc_sel_e())) // IF stage fetch address selector
    // (boot, normal, exception...)

    // jump/branch signals
    val branch_set_i: Bool = IO(Input(Bool())) // branch set signal (branch definitely
    // taken)

    val jump_set_i: Bool = IO(Input(Bool())) // jump taken set signal

    // stall & flush signals
    val stall_id_i: Bool = IO(Input(Bool()))
    val flush_id_o: Bool = IO(Output(Bool()))
  })

  /////////////////////
  // Core controller //
  /////////////////////
  object ctrl_fsm_e extends ChiselEnum {
    val
    RESET,
    BOOT_SET,
    WAIT_SLEEP,
    SLEEP,
    FIRST_FETCH,
    DECODE,
    FLUSH,
    IRQ_TAKEN,
    DBG_TAKEN_IF,
    DBG_TAKEN_ID
    = Value
  }

  val ctrl_fsm_cs: ctrl_fsm_e.Type = ctrl_fsm_e()
  val ctrl_fsm_ns: ctrl_fsm_e.Type = ctrl_fsm_e()

  {
    // Default values
    io.instr_req_o := 1.U(1)

    // The values of pc_mux and exc_pc_mux are only relevant if pc_set is set. Some of the states
    // below always set pc_mux and exc_pc_mux but only set pc_set if certain conditions are met.
    // This avoid having to factor those conditions into the pc_mux and exc_pc_mux select signals
    // helping timing.
    io.pc_mux_o := pc_sel_e.PC_BOOT
    io.pc_set_o := 0.U(1)

    ctrl_fsm_ns := ctrl_fsm_cs

    io.ctrl_busy_o := 1.U(1)

    io.controller_run_o := 0.U(1)

    {
      io.instr_req_o := 0.U(1)
      ctrl_fsm_ns := ctrl_fsm_e.RESET
      switch(ctrl_fsm_cs) {
        is(ctrl_fsm_e.RESET) {
          io.instr_req_o := 0.U(1)
          io.pc_mux_o := pc_sel_e.PC_BOOT
          io.pc_set_o := 0.U(1)
          ctrl_fsm_ns := ctrl_fsm_e.BOOT_SET
        }

        is(ctrl_fsm_e.BOOT_SET) {
          // copy boot address to instr fetch address
          io.instr_req_o := 1.U(1)
          io.pc_mux_o := pc_sel_e.PC_BOOT
          io.pc_set_o := 0.U(1)
          ctrl_fsm_ns := ctrl_fsm_e.FIRST_FETCH
        }

        is(ctrl_fsm_e.FIRST_FETCH) {
          // Stall because of IF miss
          when(io.id_in_ready_o) {
            ctrl_fsm_ns := ctrl_fsm_e.DECODE
          }
        }

        is(ctrl_fsm_e.DECODE) {
          // normal operating mode of the ID stage, in case of debug and interrupt requests,
          // priorities are as follows (lower number == higher priority)
          // 1. currently running (multicycle) instructions and exceptions caused by these
          // 2. debug requests
          // 3. interrupt requests

          io.controller_run_o := 1.U(1)

          // Set PC mux for branch and jump here to ease timing. Value is only relevant if pc_set_o is
          // also set. Setting the mux value here avoids factoring in special_req and instr_valid_i
          // which helps timing.
          io.pc_mux_o := pc_sel_e.PC_JUMP;

          when(io.branch_set_i || io.jump_set_i) {
            io.pc_set_o := 1.U(1)
          }
        }
      }
    }
  }

  ///////////////////
  // Stall control //
  ///////////////////

  // If high current instruction cannot complete this cycle. Either because it needs more cycles to
  // finish (stall_id_i) or because the writeback stage cannot accept it yet (stall_wb_i). If there
  // is no writeback stage stall_wb_i is a constant 0.
  val stall: Bool = Bool()
  stall := io.stall_id_i

  // signal to IF stage that ID stage is ready for next instr
  io.id_in_ready_o := !stall

  // kill instr in IF-ID pipeline reg that are done, or if a
  // multicycle instr causes an exception for example
  // retain_id is another kind of stall, where the instr_valid bit must remain
  // set (unless flush_id is set also). It cannot be factored directly into
  // stall as this causes a combinational loop.
  io.instr_valid_clear_o := !stall

  // update registers
  // todo
  //  always_ff @(posedge clk_i or negedge rst_ni) begin : update_regs
  //  if (!rst_ni) begin
  //  ctrl_fsm_cs    <= RESET;
  //  nmi_mode_q     <= 1'b0;
  //  debug_mode_q   <= 1'b0;
  //  load_err_q     <= 1'b0;
  //  store_err_q    <= 1'b0;
  //  exc_req_q      <= 1'b0;
  //  illegal_insn_q <= 1'b0;
  //  end else begin
  //  ctrl_fsm_cs    <= ctrl_fsm_ns;
  //  nmi_mode_q     <= nmi_mode_d;
  //  debug_mode_q   <= debug_mode_d;
  //  load_err_q     <= load_err_d;
  //  store_err_q    <= store_err_d;
  //  exc_req_q      <= exc_req_d;
  //  illegal_insn_q <= illegal_insn_d;
  //  end
  //  end


}

