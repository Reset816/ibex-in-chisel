import chiseltest._
import org.scalatest._

class CoreTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "ID test"
  it should "do ibex_id_stage" in {
    test(new ibex_core) { c =>

    }
  }
}
