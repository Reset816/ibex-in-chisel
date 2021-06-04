import chisel3._
import chiseltest._
import org.scalatest._

class InstructionMemTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Controller"
  it should "do Fetching Instruction 1" in {
    test(new InstructionMem) { c =>
      c.io.PC.poke(0.U)
      c.io.fetch_rdata_o.expect("h00_43_08_20".U)
      c.clock.step()
      c.io.PC.poke(4.U)
      c.io.fetch_rdata_o.expect("h00_44_18_21".U)
    }
  }

  it should "do Fetching Instruction 2" in {
    test(new InstructionMem) { c =>
      c.io.PC.poke(4.U)
      c.io.fetch_rdata_o.expect("h00_44_18_21".U)
    }
  }
}
