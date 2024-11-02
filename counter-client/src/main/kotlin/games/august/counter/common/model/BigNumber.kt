package games.august.counter.common.model

class BigNumber private constructor(
    val billions: Long,
    val remaining: Int,
) {
    companion object {
        private const val BILLION = 1_000_000_000

        fun create(amount: Long): BigNumber {
            val billions = amount / BILLION
            return BigNumber(
                billions = billions.toLong(),
                remaining = (amount - (billions * BILLION)).toInt(),
            )
        }

        fun create(amount: Int): BigNumber {
            val billions = amount / BILLION
            return BigNumber(
                billions = billions.toLong(),
                remaining = amount - (billions * BILLION),
            )
        }

        fun create(
            billions: Long,
            remaining: Int,
        ): BigNumber {
            val extraBillions = remaining / BILLION
            return BigNumber(
                billions = billions + extraBillions,
                remaining = remaining - (extraBillions * BILLION),
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BigNumber) return false
        return billions == other.billions && remaining == other.remaining
    }

    override fun hashCode(): Int = billions.hashCode() * 31 + remaining.hashCode()
}
