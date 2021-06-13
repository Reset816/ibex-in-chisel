import chisel3._
import chiseltest._
import org.scalatest._

class RegisterLogicTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Store and Load"
  it should "do Load 3.U in address 2" in {
    test(new ibex_register_file_fpga) { c =>
      c.io.raddr_a_i.poke(2.U) // 读2位置的值
      c.io.rdata_a_o.expect(0.U) //2位置期望得到0.U
      c.io.we_a_i.poke(true.B)
      c.io.waddr_a_i.poke(2.U) // 2位置为写入地址
      c.io.wdata_a_i.poke(3.U) // 向写入地址写入3.U

      c.clock.step()
      c.io.rdata_a_o.expect(3.U) //2位置期望得到3.U
    }
  }

}
