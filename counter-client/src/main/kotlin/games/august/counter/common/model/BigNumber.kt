package games.august.counter.common.model

class BigNumber private constructor(
    val billions: Long,
    val remaining: Int,
) {
    operator fun plus(bigNumber: BigNumber): BigNumber = add(bigNumber)

    fun add(bigNumber: BigNumber): BigNumber {
        val totalRemaining = (remaining.toLong() + bigNumber.remaining.toLong())
        val extraBillions = totalRemaining / BILLION
        val leftover = (totalRemaining % BILLION).toInt()
        return BigNumber(
            billions = billions + bigNumber.billions + extraBillions,
            remaining = leftover,
        )
    }

    companion object {
        private const val BILLION = 1_000_000_000

        val ZERO: BigNumber = BigNumber(0L, 0)

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
