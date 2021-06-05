import chisel3._
import chiseltest._
import org.scalatest._

class DataMemTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "DataMem"
  it should "do read and write data" in {
    test(new DataMem) { c =>
      c.io.addr.poke(0.U)
      c.io.data_r_req.poke(true.B)
      c.io.data_r.expect("h00_43_08_20".U)
      c.clock.step()
      c.io.data_w_req.poke(true.B)
      c.io.data_w.poke("h0F_43_11_22".U)
      c.clock.step()
      c.io.data_r_req.poke(true.B)
      c.io.data_r.expect("h0F_43_11_22".U)
    }
  }
}
