package app.dashtable

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorSpecies

object VectorSpeciesUtils {
  fun selectBestSpecies(arraySize: Int): VectorSpecies<Byte>? {
    val candidates = listOf(
      ByteVector.SPECIES_64,   // 8 bytes
      ByteVector.SPECIES_128,  // 16 bytes
      ByteVector.SPECIES_256,  // 32 bytes
      ByteVector.SPECIES_512   // 64 bytes
    )

    return candidates
      .firstOrNull { arraySize >= it.vectorByteSize() && isSpeciesSupported(it) }
  }

  private fun isSpeciesSupported(species: VectorSpecies<Byte>): Boolean {
    return try {
      val testArray = ByteArray(species.vectorByteSize()) { 0 }
      ByteVector.fromArray(species, testArray, 0)
      true
    } catch (e: Exception) {
      false
    }
  }
}