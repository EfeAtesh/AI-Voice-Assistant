package com.efea.voiceassistant.utils

class Tokenizer {
    companion object {

        private const val MAX_PHONEME_LENGTH = 512


        private val VOCAB = getVocab()


        fun tokenize(phonemes: String): LongArray {
            if (phonemes.length > MAX_PHONEME_LENGTH) {
                throw IllegalArgumentException(
                    "Text is too long, must be less than $MAX_PHONEME_LENGTH phonemes"
                )
            }

            return phonemes.map { char ->
                val symbol = char.toString()
                VOCAB[symbol]?.toLong() ?: 0L // Default to 0 if symbol not found
            }.toLongArray()
        }


        private fun getVocab(): Map<String, Int> {
            val pad = "$"
            val punctuation = ";:,.!?¡¿—…\"«»“” "
            val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val lettersIpa =
                "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ"


            val symbols = listOf(pad) +
                    punctuation.toList() +
                    letters.toList() +
                    lettersIpa.toList()


            return symbols.mapIndexed { index, symbol -> symbol.toString() to index }.toMap()
        }
    }
}
