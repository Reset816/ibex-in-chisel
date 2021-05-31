import chisel3._
import chiseltest._
import org.scalatest._

class ControllerLogicTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Controller"
  it should "do ibex_controller" in {
    test(new ibex_controller) { c =>
      c.io.branch_set_i.poke(false.B)
      c.io.jump_set_i.poke(false.B)
      c.io.stall_id_i.poke(true.B)
      c.reset.poke(true.B)

      c.io.ctrl_busy_o.expect(true.B)
      c.io.instr_valid_clear_o.expect(false.B)
      c.io.id_in_ready_o.expect(false.B)
      c.io.instr_req_o.expect(false.B)
      c.io.pc_set_o.expect(false.B)
      c.io.pc_mux_o.expect(pc_sel_e.PC_BOOT)
      c.io.branch_set_i.expect(false.B)
    }

  }

}
