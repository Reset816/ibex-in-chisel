import chisel3._
import chiseltest._
import org.scalatest._

class LSUTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "DataMem"
  it should "do read and write data" in {
    test(new LSU) { c =>
      c.io.addr_i.poke(0.U)
      c.io.read_req.poke(true.B)
      c.io.data_r_o.expect("h00_43_08_20".U)

      c.io.write_enable.poke(true.B)
      c.io.data_w_i.poke("h0F_43_11_22".U)
      c.io.read_req.poke(true.B)
      c.io.data_r_o.expect("h00_43_08_20".U)
      c.clock.step()
      c.io.data_r_o.expect("h0F_43_11_22".U)

    }
  }
}
