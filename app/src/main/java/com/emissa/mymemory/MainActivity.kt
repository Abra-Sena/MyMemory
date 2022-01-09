package com.emissa.mymemory

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.squareup.picasso.Picasso
import io.github.muddz.styleabletoast.StyleableToast
import kotlin.system.measureTimeMillis


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
    private val firebaseAnalytics = Firebase.analytics
    private val remoteConfig = Firebase.remoteConfig
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private var boardSize: BoardSize = BoardSize.EASY

    private var start: Long? = 0;
    private var end: Long? = 0;
    private var time: Long? = 0;
    private var duration: Long? = 0;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        remoteConfig.setDefaultsAsync(mapOf("scaled_height" to 250L, "compress_quality" to 60L))
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) Log.i(TAG, "Fetch succeeded, config updated? ${task.result}")
            else Log.w(TAG, "Remote config fetch failed!")
        }

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
        // handle when user didn't enter a game name
        if (customGameName.isBlank()) {
            Snackbar.make(clRoot, "Game name is empty", Snackbar.LENGTH_LONG).show()
            Log.e(TAG, "Retrieving an empty game name")
            return
        }
        firebaseAnalytics.logEvent("download_game_attempt") {
            param("game_name", customGameName)
        }

        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList =  document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, getString(R.string.game_not_found) + " '$customGameName", Snackbar.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            firebaseAnalytics.logEvent("download_game_success") {
                param("game_name", customGameName)
            }

            // a game is successfully found with the provided name => reset up the recyclerview with the custom data user requested
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            gameName = customGameName
            for (imageUrl in userImageList.images) {
                // download a game images and save them in picasso cache to avoid delay to show an image while user wants to play a custom game
                Picasso.get().load(imageUrl).fetch()
            }
            // indicate to user that they are playing a custom game
            Snackbar.make(clRoot, getString(R.string.playing_game_name) + " '$customGameName'!", Snackbar.LENGTH_SHORT).show()
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
        firebaseAnalytics.logEvent("creation_show_dialog", null)
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)

        showAlertDialog(getString(R.string.create_game), boardSizeView, View.OnClickListener {
            // set value for the board size
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                R.id.rbHard -> BoardSize.HARD
                else ->  BoardSize.EXTREME
            }
            firebaseAnalytics.logEvent("creation_start_activity") {
                param("board_size", desiredBoardSize.name)
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
            BoardSize.EXTREME -> radioGroupSize.check(R.id.rbExtreme)
        }

        showAlertDialog(getString(R.string.choose_game_size), boardSizeView, View.OnClickListener {
            // set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                R.id.rbHard -> BoardSize.HARD
                else ->  BoardSize.EXTREME
            }
            // reset values every time user goes back to custom game
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
        memoryGame = MemoryGame(boardSize, customGameImages)
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
            BoardSize.EXTREME -> {
                tvNumMoves.text = getString(R.string.extreme) + " 6 x 5"
                tvNumPairs.text = getString(R.string.pairs) + " 0 / 15"
            }
        }

        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        //pass the doubled chosen images to the adapter
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener {
            override fun onCardClickListener(position: Int) {
                // record time stamp between game start and its end
                if (memoryGame.numPairsFound == 0) start = System.currentTimeMillis()

                updateGameWithFlip(position)

                if (memoryGame.haveWonGame()) {
                    end = System.currentTimeMillis()
                    duration = start?.let { end?.minus(it) }

                    // handle game duration and notify user with a Toast on how long the game lasted
                    time = duration?.div(1000)?.plus(1)
                    StyleableToast.makeText(applicationContext,
                        getString(R.string.game_time) +" ${time?.toInt()} s",
                        Toast.LENGTH_LONG, R.style.myToast
                    ).show();
                }
            }
        })

        rvBoard.adapter = adapter
        //performance optimization
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    @SuppressLint("ResourceAsColor")
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
            tvNumPairs.text =
                getString(R.string.pairs) + " ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"

            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, getString(R.string.game_win), Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(
                    clRoot, intArrayOf(
                        Color.argb(57, 44, 145, 83),
                        Color.argb(52, 133, 13, 70),
                        Color.argb(82, 209, 18, 119),
                        Color.argb(42, 35, 107, 17),
                        Color.MAGENTA, Color.BLUE, Color.GREEN, Color.RED
                    )
                ).oneShot()
                firebaseAnalytics.logEvent("won_game") {
                    param("game_name", gameName ?: getString(R.string.app_name))
                    param("board_size", boardSize.name)
                }
            }
        }

        //show how many moves the user has made
        tvNumMoves.text = getString(R.string.moves) + " ${memoryGame.getNumMoves()}"
        //tell the recyclerview adapter that the context is changed so it should update itself
        adapter.notifyDataSetChanged()
    }
}