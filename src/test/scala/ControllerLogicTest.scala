import chisel3._
import chiseltest._
import org.scalatest._

class ControllerLogicTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Controller"
  it should "do ibex_controller" in {
    test(new ibex_controller) { c =>
      // Init
      c.io.branch_set_i.poke(false.B)
      c.io.jump_set_i.poke(false.B)
      c.io.stall_id_i.poke(true.B)

      // Enter RESET status
      c.reset.poke(true.B)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(false.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(true.B)
      c.io.controller_run_o.expect(false.B)

      // Enter BOOT_SET status
      c.clock.step()
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(true.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(false.B)
      // Enter FIRST_FETCH status
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.FIRST_FETCH)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(true.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(true.B) // ~stall_id_i
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(false.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(true.B)
      // Failed entering DECODE status and still in FIRST_FETCH because id_in_ready_o == false
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.FIRST_FETCH)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(false.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(false.B)
      // Enter DECODE status because id_in_ready_o == true
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.DECODE)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(true.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(true.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_set_o.expect(false.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_JUMP)
      c.io.controller_run_o.expect(true.B)
    }
  }

  it should "do ibex_controller2" in {
    test(new ibex_controller) { c =>
      // Init
      c.io.branch_set_i.poke(false.B)
      c.io.jump_set_i.poke(true.B)
      c.io.stall_id_i.poke(true.B)

      // Enter RESET status
      c.reset.poke(true.B)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(false.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(true.B)
      c.io.controller_run_o.expect(false.B)

      // Enter BOOT_SET status
      c.clock.step()
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(true.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(false.B)
      // Enter FIRST_FETCH status
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.FIRST_FETCH)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(true.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(true.B) // ~stall_id_i
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(false.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(true.B)
      // Failed entering DECODE status and still in FIRST_FETCH because id_in_ready_o == false
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.FIRST_FETCH)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(false.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.pc_set_o.expect(false.B)
      c.io.controller_run_o.expect(false.B)

      c.io.stall_id_i.poke(false.B)
      // Enter DECODE status because id_in_ready_o == true
      c.clock.step()
      //      c.io.test.expect(ctrl_fsm_e.DECODE)
      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(true.B) // ~stall_id_i
      c.io.id_in_ready_o.expect(true.B) // ~stall_id_i
      c.io.instr_req_o.expect(true.B)
      c.io.pc_set_o.expect(true.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_JUMP)
      c.io.controller_run_o.expect(true.B)
    }
  }
}
