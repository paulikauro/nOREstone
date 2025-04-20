package com.sloimay.norestone

class RollingSampleAnalyser(
    private val maxSampleCount: Int,
) {

    private val samples = DoubleArray(maxSampleCount)

    // Always points to the last sample
    private var idx = 0

    private var sampleCount = 0

    fun addSample(sample: Double) {
        idx = addCircular(idx, 1)
        samples[idx] = sample
        if (sampleCount < maxSampleCount) sampleCount += 1
    }


    fun getWindowAvg(samplesBack: Int): Double {
        // not sure if its better to throw an error or fail with a 0.0
        if (sampleCount == 0) return 0.0
        if (samplesBack == 0) return 0.0

        val sampleAmountToAdd = samplesBack.clamp(0, sampleCount)
        var acc = 0.0
        for (i in 0 until sampleAmountToAdd) {
            val sampleIdx = (idx + samples.size - i) % samples.size
            val sample = samples[sampleIdx]
            acc += sample
        }

        return acc / sampleAmountToAdd.toDouble()
    }


    private fun addCircular(i: Int, a: Int) = (i + a) % samples.size

}