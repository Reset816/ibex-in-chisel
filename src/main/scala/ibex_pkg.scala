import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
/////////////////////
// Parameter Enums //
/////////////////////

object regfile_e extends ChiselEnum {
  val RegFileFF = Value(0.U)
  val RegFileFPGA = Value(1.U)
  val RegFileLatch = Value(2.U)
}

object rv32m_e extends ChiselEnum {
  val RV32MNone = Value(0.U)
  val RV32MSlow = Value(1.U)
  val RV32MFast = Value(2.U)
  val RV32MSingleCycle = Value(3.U)
}

object rv32b_e extends ChiselEnum {
  val RV32BNone = Value(0.U)
  val RV32BBalanced = Value(1.U)
  val RV32BFull = Value(2.U)
}

/////////////
// Opcodes //
/////////////

object opcode_e extends ChiselEnum {
  val OPCODE_LOAD = Value(0x03.U(7.W))
  val OPCODE_MISC_MEM = Value(0x0f.U(7.W))
  val OPCODE_OP_IMM = Value(0x13.U(7.W))
  val OPCODE_AUIPC = Value(0x17.U(7.W))
  val OPCODE_STORE = Value(0x23.U(7.W))
  val OPCODE_OP = Value(0x33.U(7.W))
  val OPCODE_LUI = Value(0x37.U(7.W))
  val OPCODE_BRANCH = Value(0x63.U(7.W))
  val OPCODE_JALR = Value(0x67.U(7.W))
  val OPCODE_JAL = Value(0x6f.U(7.W))
  val OPCODE_SYSTEM = Value(0x73.U(7.W))
}


////////////////////
// ALU operations //
////////////////////

object alu_op_e extends ChiselEnum {
  // Arithmetics
  val
  ALU_ADD,
  ALU_SUB,

  // Logics
  ALU_XOR,
  ALU_OR,
  ALU_AND,
  // RV32B
  ALU_XNOR,
  ALU_ORN,
  ALU_ANDN,

  // Shifts
  ALU_SRA,
  ALU_SRL,
  ALU_SLL,
  // RV32B
  ALU_SRO,
  ALU_SLO,
  ALU_ROR,
  ALU_ROL,
  ALU_GREV,
  ALU_GORC,
  ALU_SHFL,
  ALU_UNSHFL,

  // Comparisons
  ALU_LT,
  ALU_LTU,
  ALU_GE,
  ALU_GEU,
  ALU_EQ,
  ALU_NE,
  // RV32B
  ALU_MIN,
  ALU_MINU,
  ALU_MAX,
  ALU_MAXU,

  // Pack
  // RV32B
  ALU_PACK,
  ALU_PACKU,
  ALU_PACKH,

  // Sign-Extend
  // RV32B
  ALU_SEXTB,
  ALU_SEXTH,

  // Bitcounting
  // RV32B
  ALU_CLZ,
  ALU_CTZ,
  ALU_PCNT,

  // Set lower than
  ALU_SLT,
  ALU_SLTU,

  // Ternary Bitmanip Operations
  // RV32B
  ALU_CMOV,
  ALU_CMIX,
  ALU_FSL,
  ALU_FSR,

  // Single-Bit Operations
  // RV32B
  ALU_SBSET,
  ALU_SBCLR,
  ALU_SBINV,
  ALU_SBEXT,

  // Bit Extract / Deposit
  // RV32B
  ALU_BEXT,
  ALU_BDEP,

  // Bit Field Place
  // RV32B
  ALU_BFP,

  // Carry-less Multiply
  // RV32B
  ALU_CLMUL,
  ALU_CLMULR,
  ALU_CLMULH,

  // Cyclic Redundancy Check
  ALU_CRC32_B,
  ALU_CRC32C_B,
  ALU_CRC32_H,
  ALU_CRC32C_H,
  ALU_CRC32_W,
  ALU_CRC32C_W = Value
}

object md_op_e extends ChiselEnum {
  // Multiplier/divider
  val
  MD_OP_MULL,
  MD_OP_MULH,
  MD_OP_DIV,
  MD_OP_REM = Value
}


//////////////////////////////////
// Control and status registers //
//////////////////////////////////

// CSR operations
object csr_op_e extends ChiselEnum {
  // Multiplier/divider
  val
  CSR_OP_READ,
  CSR_OP_WRITE,
  CSR_OP_SET,
  CSR_OP_CLEAR = Value
}

object priv_lvl_e extends ChiselEnum {
  val PRIV_LVL_M = Value("b11".U(2.W))
  val PRIV_LVL_H = Value("b10".U(2.W))
  val PRIV_LVL_S = Value("b01".U(2.W))
  val PRIV_LVL_U = Value("b00".U(2.W))
}

// Constants for the dcsr.xdebugver fields
object x_debug_ver_e extends ChiselEnum {
  val XDEBUGVER_NO = Value(0.U(4.W))
  val XDEBUGVER_STD = Value(4.U(4.W))
  val XDEBUGVER_NONSTD = Value(15.U(4.W))
}

//////////////
// WB stage //
//////////////

// Type of instruction present in writeback stage
object wb_instr_type_e extends ChiselEnum {
  val
  WB_INSTR_LOAD, // Instruction is awaiting load data
  WB_INSTR_STORE, // Instruction is awaiting store response
  WB_INSTR_OTHER // Instruction doesn't fit into above categories
  = Value
}

//////////////
// ID stage //
//////////////

// Operand a selection
object op_a_sel_e extends ChiselEnum {
  val
  OP_A_REG_A,
  OP_A_FWD,
  OP_A_CURRPC,
  OP_A_IMM
  = Value
}

// Immediate a selection
object imm_a_sel_e extends ChiselEnum {
  val
  IMM_A_Z,
  IMM_A_ZERO
  = Value
}

// Operand b selection
object op_b_sel_e extends ChiselEnum {
  val
  OP_B_REG_B,
  OP_B_IMM
  = Value
}

// Immediate b selection
object imm_b_sel_e extends ChiselEnum {
  val
  IMM_B_I,
  IMM_B_S,
  IMM_B_B,
  IMM_B_U,
  IMM_B_J,
  IMM_B_INCR_PC,
  IMM_B_INCR_ADDR
  = Value
}

// Regfile write data selection
object rf_wd_sel_e extends ChiselEnum {
  val
  RF_WD_EX,
  RF_WD_CSR
  = Value
}

//////////////
// IF stage //
//////////////

// PC mux selection
object pc_sel_e extends ChiselEnum {
  val
  PC_BOOT,
  PC_JUMP,
  PC_EXC,
  PC_ERET,
  PC_DRET,
  PC_BP
  = Value
}

// Exception PC mux selection
object exc_pc_sel_e extends ChiselEnum {
  val
  EXC_PC_EXC,
  EXC_PC_IRQ,
  EXC_PC_DBD,
  EXC_PC_DBG_EXC // Exception while in debug mode
  = Value
}

// Interrupt requests
class irqs_t extends Bundle {
  val irq_software = UInt(1.W)
  val irq_timer = UInt(1.W)
  val irq_external = UInt(1.W)
  val irq_fast = UInt(15.W) // 15 fast interrupts,
  // one interrupt is reserved for NMI (not visible through mip/mie)
}

// Exception cause
object exc_cause_e extends ChiselEnum {
  val EXC_CAUSE_IRQ_SOFTWARE_M = Value(Cat("b1".U(1.W), 3.U(5.W)))
  val EXC_CAUSE_IRQ_TIMER_M = Value(Cat("b1".U(1.W), 7.U(5.W)))
  val EXC_CAUSE_IRQ_EXTERNAL_M = Value(Cat("b1".U(1.W), 11.U(5.W)))
  //  val EXC_CAUSE_IRQ_FAST_0 = Value(Cat("b1".U(1.W), 16.U(5.W)))
  //  val EXC_CAUSE_IRQ_FAST_14 = Value(Cat("b1".U(1.W), 30.U(5.W)))
  val EXC_CAUSE_IRQ_NM = Value(Cat("b1".U(1.W), 31.U(5.W)))
  val EXC_CAUSE_INSN_ADDR_MISA = Value(Cat("b0".U(1.W), 0.U(5.W)))
  val EXC_CAUSE_INSTR_ACCESS_FAULT = Value(Cat("b0".U(1.W), 1.U(5.W)))
  val EXC_CAUSE_ILLEGAL_INSN = Value(Cat("b0".U(1.W), 2.U(5.W)))
  val EXC_CAUSE_BREAKPOINT = Value(Cat("b0".U(1.W), 3.U(5.W)))
  val EXC_CAUSE_LOAD_ACCESS_FAULT = Value(Cat("b0".U(1.W), 5.U(5.W)))
  val EXC_CAUSE_STORE_ACCESS_FAULT = Value(Cat("b0".U(1.W), 7.U(5.W)))
  val EXC_CAUSE_ECALL_UMODE = Value(Cat("b0".U(1.W), 8.U(5.W)))
  val EXC_CAUSE_ECALL_MMODE = Value(Cat("b0".U(1.W), 11.U(5.W)))
}

// Debug cause
object dbg_cause_e extends ChiselEnum {
  val DBG_CAUSE_NONE = Value(0x0.U(3.W))
  val DBG_CAUSE_EBREAK = Value(0x1.U(3.W))
  val DBG_CAUSE_TRIGGER = Value(0x2.U(3.W))
  val DBG_CAUSE_HALTREQ = Value(0x3.U(3.W))
  val DBG_CAUSE_STEP = Value(0x4.U(3.W))
}
//
////// PMP constants
////val PMP_MAX_REGIONS      = 16.U
////val PMP_CFG_W            = 8.U
////
////// PMP acces type
////val PMP_I      = 0.U
////val PMP_D            = 1.U
//
//object pmp_req_e extends ChiselEnum {
//  val PMP_ACC_EXEC = Value("b00".U(2.W))
//  val PMP_ACC_WRITE = Value("b01".U(2.W))
//  val PMP_ACC_READ = Value("b10".U(2.W))
//}
//
//// PMP cfg structures
//object pmp_cfg_mode_e extends ChiselEnum {
//  val PMP_MODE_OFF = Value("b00".U(2.W))
//  val PMP_MODE_TOR = Value("b01".U(2.W))
//  val PMP_MODE_NA4 = Value("b10".U(2.W))
//  val PMP_MODE_NAPOT = Value("b11".U(2.W))
//}
//
//class pmp_cfg_t extends Bundle {
//  val lock = UInt(1.W)
//  val mode = new pmp_cfg_mode_e()
//  val exec = UInt(1.W)
//  val write = UInt(1.W)
//  val read = UInt(1.W)
//}
