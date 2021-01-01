package com.emissa.mymemory.models

import com.emissa.mymemory.utils.DEFAULT_ICONS

class MemoryGame (private val boardSize: BoardSize) {

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardsflips = 0
    private var indexOfSingleSelectedCard: Int? = null

    init {
        //pass into the adapter the list of images icons, the drawable that should make up the game board
        val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
        // double the chosen images
        val randomizedImages = (chosenImages + chosenImages).shuffled()
        // make a list of the randomized chosen images
        cards = randomizedImages.map { MemoryCard(it) }
    }

    fun flipCard(position: Int): Boolean {
        numCardsflips++
        val card = cards[position]
        var foundMatch = false

        if (indexOfSingleSelectedCard == null) {
            // 0 or 2 cards are previously flipped over => restore the cards face down and flip over the selected card
            restoreCards()
            indexOfSingleSelectedCard = position
        } else {
            // exactly 1 card is previously flipped over => flip over the selected card and check if they match
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
            indexOfSingleSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++

        return true
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched){
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numCardsflips / 2
    }
}
