package com.emissa.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emissa.mymemory.models.BoardSize
import com.emissa.mymemory.models.MemoryGame
import com.emissa.mymemory.models.UserImageList
import com.emissa.mymemory.utils.EXTRA_BOARD_SIZE
import com.emissa.mymemory.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 228
    }

    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private var boardSize: BoardSize = BoardSize.EASY


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setUpBoard()
    }


    // inflate menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miRefresh -> {
                //notify user that they are about to lose their progress on the game when the click on refresh
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog(getString(R.string.quit_game), null, View.OnClickListener {
                        setUpBoard()
                    })
                } else {
                    setUpBoard()
                }
            }
            R.id.miNewSize -> {
                showNewSizeDialog()
                return true
            }
            R.id.miCustomGame -> {
                showCreationDialog()
                return true
            }
            R.id.miDownload -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)

            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList =  document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, getString(R.string.game_not_found) + " '$customGameName", Snackbar.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // a game is founded successfully with the provided name => reset up the recyclerview with the custom data user requested
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch() // download a game images and save them in picasso cache to avoid delay to show an image while user wants to play a custom game
            }
            // indicate to user that they are playing a custom game
            Snackbar.make(clRoot, getString(R.string.playing_game_name) + " '$customGameName'!", Snackbar.LENGTH_SHORT).show()
            gameName = customGameName
            setUpBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showDownloadDialog() {
        // user will enter in the name of the game they want to play
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog(getString(R.string.fetch_game), boardDownloadView, View.OnClickListener {
            // grab the value test of the game name that user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog(getString(R.string.create_game), boardSizeView, View.OnClickListener {
            // set value for the board size
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else ->  BoardSize.HARD
            }
            // navigate user to create activity where they can set up their game
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        //view to allow user to pick the desired board size
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        // automatically select current board size when the dialog opened
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }

        showAlertDialog(getString(R.string.choose_game_size), boardSizeView, View.OnClickListener {
            // set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else ->  BoardSize.HARD
            }
            // reset values everytime user goes back to custom game
            gameName = null
            customGameImages = null
            setUpBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun setUpBoard() {
        // handle game name: show custom game name when needed instead of default name 'My Memory'
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = getString(R.string.easy) + " 4 x 2"
                tvNumPairs.text = getString(R.string.pairs) + " 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = getString(R.string.medium) + " 6 x 3"
                tvNumPairs.text = getString(R.string.pairs) + " 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = getString(R.string.hard) + " 6 x 4"
                tvNumPairs.text = getString(R.string.pairs) + " 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))

        memoryGame = MemoryGame(boardSize, customGameImages)

        //pass the doubled chosen images to the adapter
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener {
            override fun onCardClickListener(position: Int) {
                updateGameWithFlip(position)
            }

        })
        rvBoard.adapter = adapter
        //performance optimization
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {
        // Error checking
        if (memoryGame.haveWonGame()) {
            // alert user of an invalid move: user matched all cards and is trying to flip down/over a card
            Snackbar.make(clRoot, getString(R.string.game_over), Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            // alert user of an invalid move: user click on a card with face up while match is not found and game is not over
            Snackbar.make(clRoot, getString(R.string.invalid_move), Snackbar.LENGTH_SHORT).show()
            return
        }

        // Flip over a card
        // handle what happen at this stage of the game when the memory card at this position is flipped
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found ${memoryGame.numPairsFound}")

            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
                ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = getString(R.string.pairs) + " ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"

            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, getString(R.string.game_win), Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.BLACK, Color.MAGENTA, Color.BLUE, Color.GREEN)).oneShot()
            }
        }

        //show how many moves the user has made
        tvNumMoves.text = getString(R.string.moves) + " ${memoryGame.getNumMoves()}"
        //tell the recyclerview adapter that the context is changed so it should update itself
        adapter.notifyDataSetChanged()
    }
}