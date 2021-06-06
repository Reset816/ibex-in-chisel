import chiseltest._
import org.scalatest._

class IDTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ID test"
  it should "do ibex_id_stage" in {
    test(new ibex_id_stage) { c =>

    }
  }
}
