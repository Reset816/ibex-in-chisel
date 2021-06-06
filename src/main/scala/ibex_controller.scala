import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util.{is, switch}

class ibex_controller extends Module {
  val io = IO(new Bundle {

    val ctrl_busy_o: Bool = Output(Bool()) // core is busy processing instrs

    // to IF-ID pipeline stage
    val instr_valid_clear_o: Bool = Output(Bool()) // kill instr in IF-ID reg

    val id_in_ready_o: Bool = Output(Bool()) // ID stage is ready for new instr

    val controller_run_o: Bool = Output(Bool()) // Controller is in standard instruction
    // run mode

    // to prefetcher
    val instr_req_o: Bool = Output(Bool()) // start fetching instructions

    val pc_set_o: Bool = Output(Bool()) // jump to address set by pc_mux

    val pc_mux_o: pc_sel_e.Type = Output(pc_sel_e()) // IF stage fetch address selector
    // (boot, normal, exception...)

    // jump/branch signals
    val branch_set_i: Bool = Input(Bool()) // branch set signal (branch definitely
    // taken)

    val jump_set_i: Bool = Input(Bool()) // jump taken set signal

    // stall & flush signals
    val stall_id_i: Bool = Input(Bool())

    //    val test : ctrl_fsm_e.Type = Output(ctrl_fsm_e())
  })

  object ctrl_fsm_e extends ChiselEnum {
    val
    RESET,
    BOOT_SET,
    FIRST_FETCH,
    DECODE
    = Value
  }

  /////////////////////
  // Core controller //
  /////////////////////

  val ctrl_fsm_cs: ctrl_fsm_e.Type = Wire(ctrl_fsm_e())
  val ctrl_fsm_ns: ctrl_fsm_e.Type = Wire(ctrl_fsm_e())

  {
    // Default values
    io.instr_req_o := 1.U(1.W)

    // The values of pc_mux and exc_pc_mux are only relevant if pc_set is set. Some of the states
    // below always set pc_mux and exc_pc_mux but only set pc_set if certain conditions are met.
    // This avoid having to factor those conditions into the pc_mux and exc_pc_mux select signals
    // helping timing.
    io.pc_mux_o := pc_sel_e.PC_BOOT
    io.pc_set_o := 0.U(1.W)

    ctrl_fsm_ns := ctrl_fsm_cs

    io.ctrl_busy_o := 1.U(1.W)

    io.controller_run_o := 0.U(1.W)

    {
      io.instr_req_o := 0.U(1.W)
      ctrl_fsm_ns := ctrl_fsm_e.RESET

      switch(ctrl_fsm_cs) {

        is(ctrl_fsm_e.RESET) {
          io.instr_req_o := 0.U(1.W)
          io.pc_mux_o := pc_sel_e.PC_BOOT
          io.pc_set_o := 1.U(1.W)
          ctrl_fsm_ns := ctrl_fsm_e.BOOT_SET
        }

        is(ctrl_fsm_e.BOOT_SET) {
          // copy boot address to instr fetch address
          io.instr_req_o := 1.U(1.W)
          io.pc_mux_o := pc_sel_e.PC_BOOT
          io.pc_set_o := 1.U(1.W)
          ctrl_fsm_ns := ctrl_fsm_e.FIRST_FETCH
        }

        is(ctrl_fsm_e.FIRST_FETCH) {
          // Stall because of IF miss
          io.instr_req_o := 1.U(1.W) // What the fuck, Why it need this line?
          when(io.id_in_ready_o) {
            ctrl_fsm_ns := ctrl_fsm_e.DECODE
          }.otherwise {
            ctrl_fsm_ns := ctrl_fsm_e.FIRST_FETCH // What the fuck, Why it need this line?
          }
        }

        is(ctrl_fsm_e.DECODE) {
          // normal operating mode of the ID stage, in case of debug and interrupt requests,
          // priorities are as follows (lower number == higher priority)
          // 1. currently running (multicycle) instructions and exceptions caused by these
          // 2. debug requests
          // 3. interrupt requests
          io.instr_req_o := 1.U(1.W) // What the fuck, Why it need this line?
          io.controller_run_o := 1.U(1.W)

          // Set PC mux for branch and jump here to ease timing. Value is only relevant if pc_set_o is
          // also set. Setting the mux value here avoids factoring in special_req and instr_valid_i
          // which helps timing.
          io.pc_mux_o := pc_sel_e.PC_JUMP;

          when(io.branch_set_i || io.jump_set_i) {
            io.pc_set_o := 1.U(1.W)
          }
        }
      }
    }
  }
  //  io.test := ctrl_fsm_cs

  ///////////////////
  // Stall control //
  ///////////////////

  // If high current instruction cannot complete this cycle. Either because it needs more cycles to
  // finish (stall_id_i) or because the writeback stage cannot accept it yet (stall_wb_i). If there
  // is no writeback stage stall_wb_i is a constant 0.
  val stall: Bool = Wire(Bool())
  stall := io.stall_id_i

  // signal to IF stage that ID stage is ready for next instr
  io.id_in_ready_o := !stall

  // kill instr in IF-ID pipeline reg that are done, or if a
  // multicycle instr causes an exception for example
  // retain_id is another kind of stall, where the instr_valid bit must remain
  // set (unless flush_id is set also). It cannot be factored directly into
  // stall as this causes a combinational loop.
  io.instr_valid_clear_o := !stall

  val reset_n: AsyncReset = (!reset.asBool).asAsyncReset

  // update registers
  ctrl_fsm_cs := withReset(reset_n) {
    RegNext(ctrl_fsm_ns, init = ctrl_fsm_e.RESET)
  }

  //  io.test := withReset(reset_n) {
  //    RegNext(ctrl_fsm_ns, init = ctrl_fsm_e.RESET)
  //  }
}

