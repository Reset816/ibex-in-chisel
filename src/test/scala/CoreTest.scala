import chiseltest._
import org.scalatest._

class CoreTest extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Core test"
  it should "do ibex_Core_test" in {
    test(new ibex_core) { c =>

    }
  }
}
