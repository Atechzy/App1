package com.earning.games

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoinBalance: TextView
    private lateinit var gameListScrollView: View
    private lateinit var gameArenaContainer: View
    private lateinit var gameListContainer: LinearLayout
    private lateinit var gameDynamicContent: FrameLayout
    private lateinit var tvActiveGameTitle: TextView
    private lateinit var tvActiveGameChances: TextView
    private lateinit var offlineBlocker: View

    private var activeGameIndex: Int = -1
    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false

    private val prefs by lazy { getSharedPreferences("CoinRewardsPrefs", Context.MODE_PRIVATE) }

    // List of 21 Mini Games
    private val miniGames = listOf(
        "Math Blitz (Quick Addition)",
        "Speed Tapper (15 Taps in 5s)",
        "Higher or Lower",
        "Rock Paper Scissors",
        "Color Stroop Challenge",
        "Odd One Out",
        "Lucky Dice 7",
        "Coin Flip Predictor",
        "Number Guesser (1-50)",
        "Reaction Timer (Tap on Green)",
        "Word Unscramble",
        "Scratch Card Match",
        "Fortune Wheel Spin",
        "Tic-Tac-Toe vs Bot",
        "Memory Digit Span",
        "Math True or False",
        "Emoji Pair Match",
        "Tap The Target",
        "Trivia Quick Quiz",
        "Simon Pattern Memory",
        "Energy Sprint (Mash 100%)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initMobileAds()
        setupInternetWatcher()
        buildGameList()
        updateCoinDisplay()

        findViewById<Button>(R.id.btnWithdraw).setOnClickListener { showWithdrawDialog() }
        findViewById<Button>(R.id.btnBackToHub).setOnClickListener { closeGameArena() }
        findViewById<Button>(R.id.btnRetryInternet).setOnClickListener { checkInternetStatus() }
    }

    private fun initViews() {
        tvCoinBalance = findViewById(R.id.tvCoinBalance)
        gameListScrollView = findViewById(R.id.gameListScrollView)
        gameArenaContainer = findViewById(R.id.gameArenaContainer)
        gameListContainer = findViewById(R.id.gameListContainer)
        gameDynamicContent = findViewById(R.id.gameDynamicContent)
        tvActiveGameTitle = findViewById(R.id.tvActiveGameTitle)
        tvActiveGameChances = findViewById(R.id.tvActiveGameChances)
        offlineBlocker = findViewById(R.id.offlineBlocker)

        // Initialize starting coins if first run
        if (!prefs.contains("user_coins")) {
            prefs.edit().putInt("user_coins", 100).apply()
        }
    }

    // --- INTERNET ENFORCEMENT ---
    private fun setupInternetWatcher() {
        checkInternetStatus()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { offlineBlocker.visibility = View.GONE }
            }
            override fun onLost(network: Network) {
                runOnUiThread { offlineBlocker.visibility = View.VISIBLE }
            }
        })
    }

    private fun checkInternetStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        offlineBlocker.visibility = if (isOnline) View.GONE else View.VISIBLE
    }

    // --- ADMOB INTEGRATION (TEST IDS) ---
    private fun initMobileAds() {
        MobileAds.initialize(this) {}

        // Load Banner Ad
        val adView = AdView(this).apply {
            adUnitId = "ca-app-pub-3940256099942544/6300978111" // Google Banner Test ID
            setAdSize(AdSize.BANNER)
        }
        findViewById<FrameLayout>(R.id.bannerAdContainer).addView(adView)
        adView.loadAd(AdRequest.Builder().build())

        loadRewardedAd()
    }

    private fun loadRewardedAd() {
        if (rewardedAd != null || isLoadingAd) return
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            this,
            "ca-app-pub-3940256099942544/5224354917", // Google Rewarded Test ID
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadingAd = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    private fun showRewardedAdForChances(gameId: Int) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                }
            }
            rewardedAd?.show(this) {
                addChances(gameId, 5)
                Toast.makeText(this, "🎉 +5 Chances Added!", Toast.LENGTH_SHORT).show()
                updateArenaChances(gameId)
                buildGameList()
            }
        } else {
            Toast.makeText(this, "Ad is loading, please try again in a few seconds...", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    // --- COIN & CHANCES MANAGEMENT ---
    private fun getCoins(): Int = prefs.getInt("user_coins", 0)

    private fun addCoins(amount: Int) {
        val updated = getCoins() + amount
        prefs.edit().putInt("user_coins", updated).apply()
        updateCoinDisplay()
        Toast.makeText(this, "🪙 +$amount Coins Earned!", Toast.LENGTH_SHORT).show()
    }

    private fun updateCoinDisplay() {
        tvCoinBalance.text = "🪙 ${getCoins()} Coins"
    }

    private fun getChances(gameId: Int): Int = prefs.getInt("chances_$gameId", 5)

    private fun consumeChance(gameId: Int): Boolean {
        val current = getChances(gameId)
        if (current <= 0) return false
        prefs.edit().putInt("chances_$gameId", current - 1).apply()
        updateArenaChances(gameId)
        return true
    }

    private fun addChances(gameId: Int, count: Int) {
        val current = getChances(gameId)
        prefs.edit().putInt("chances_$gameId", current + count).apply()
    }

    private fun updateArenaChances(gameId: Int) {
        tvActiveGameChances.text = "Chances: ${getChances(gameId)}"
    }

    // --- GAME HUB & SELECTION ---
    private fun buildGameList() {
        gameListContainer.removeAllViews()
        miniGames.forEachIndexed { index, name ->
            val card = CardView(this).apply {
                radius = 16f
                setCardBackgroundColor(Color.parseColor("#1E1E1E"))
                useCompatPadding = true
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 28, 32, 28)
            }

            val title = TextView(this).apply {
                text = "${index + 1}. $name"
                setTextColor(Color.WHITE)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val chancesBadge = TextView(this).apply {
                text = "⚡ ${getChances(index)}"
                setTextColor(Color.parseColor("#00E676"))
                textSize = 13f
                setPadding(16, 8, 16, 8)
            }

            val playBtn = Button(this).apply {
                text = "Play"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#6200EE"))
                setOnClickListener { launchGame(index) }
            }

            row.addView(title)
            row.addView(chancesBadge)
            row.addView(playBtn)
            card.addView(row)
            gameListContainer.addView(card)
        }
    }

    private fun launchGame(index: Int) {
        if (getChances(index) <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Out of Chances!")
                .setMessage("You have 0 chances left for this game. Watch a short sponsored video ad to claim 5 more chances?")
                .setPositiveButton("Watch Ad (+5)") { _, _ -> showRewardedAdForChances(index) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        activeGameIndex = index
        tvActiveGameTitle.text = miniGames[index]
        updateArenaChances(index)

        gameListScrollView.visibility = View.GONE
        gameArenaContainer.visibility = View.VISIBLE
        renderGameContent(index)
    }

    private fun closeGameArena() {
        gameArenaContainer.visibility = View.GONE
        gameListScrollView.visibility = View.VISIBLE
        gameDynamicContent.removeAllViews()
        buildGameList()
    }

    // --- DYNAMIC ENGINE FOR 21 MINI GAMES ---
    private fun renderGameContent(index: Int) {
        gameDynamicContent.removeAllViews()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        when (index) {
            0 -> buildMathBlitz(container)
            1 -> buildSpeedTapper(container)
            2 -> buildHigherLower(container)
            3 -> buildRockPaperScissors(container)
            4 -> buildColorMatch(container)
            5 -> buildOddOneOut(container)
            6 -> buildDice7(container)
            7 -> buildCoinFlip(container)
            8 -> buildNumberGuesser(container)
            9 -> buildReactionTimer(container)
            10 -> buildWordUnscramble(container)
            11 -> buildScratchCard(container)
            12 -> buildWheelSpin(container)
            13 -> buildTicTacToe(container)
            14 -> buildMemoryDigits(container)
            15 -> buildMathTrueFalse(container)
            16 -> buildEmojiMatch(container)
            17 -> buildTapTarget(container)
            18 -> buildTriviaQuiz(container)
            19 -> buildSimonSays(container)
            20 -> buildEnergySprint(container)
        }
        gameDynamicContent.addView(container)
    }

    // --- GAME IMPLEMENTATIONS ---

    // 1. Math Blitz
    private fun buildMathBlitz(layout: LinearLayout) {
        val a = Random.nextInt(10, 50)
        val b = Random.nextInt(10, 50)
        val correct = a + b
        val prompt = TextView(this).apply {
            text = "Solve: $a + $b = ?"
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        layout.addView(prompt)

        val answers = mutableListOf(correct, correct + 3, correct - 2, correct + 5).shuffled()
        answers.forEach { ans ->
            Button(this).apply {
                text = "$ans"
                setOnClickListener {
                    if (!consumeChance(0)) { launchGame(0); return@setOnClickListener }
                    if (ans == correct) addCoins(20) else Toast.makeText(this@MainActivity, "Wrong answer!", Toast.LENGTH_SHORT).show()
                    renderGameContent(0)
                }
                layout.addView(this)
            }
        }
    }

    // 2. Speed Tapper
    private fun buildSpeedTapper(layout: LinearLayout) {
        var taps = 0
        val target = 15
        val info = TextView(this).apply { text = "Tap 15 times before 5 seconds expire!"; setTextColor(Color.WHITE); textSize = 16f }
        val counter = TextView(this).apply { text = "Taps: 0 / $target"; setTextColor(Color.YELLOW); textSize = 22f; setPadding(0, 20, 0, 20) }
        var timerStarted = false
        var timer: CountDownTimer? = null

        val tapBtn = Button(this).apply {
            text = "TAP HERE!"
            setOnClickListener {
                if (!timerStarted) {
                    if (!consumeChance(1)) { launchGame(1); return@setOnClickListener }
                    timerStarted = true
                    timer = object : CountDownTimer(5000, 1000) {
                        override fun onTick(ms: Long) { info.text = "Time left: ${ms / 1000}s" }
                        override fun onFinish() {
                            isEnabled = false
                            if (taps >= target) addCoins(25) else Toast.makeText(this@MainActivity, "Time up!", Toast.LENGTH_SHORT).show()
                            Handler(Looper.getMainLooper()).postDelayed({ renderGameContent(1) }, 1500)
                        }
                    }.start()
                }
                taps++
                counter.text = "Taps: $taps / $target"
                if (taps >= target) {
                    timer?.cancel()
                    isEnabled = false
                    addCoins(25)
                    Handler(Looper.getMainLooper()).postDelayed({ renderGameContent(1) }, 1500)
                }
            }
        }
        layout.addView(info)
        layout.addView(counter)
        layout.addView(tapBtn)
    }

    // 3. Higher or Lower
    private fun buildHigherLower(layout: LinearLayout) {
        val currentNum = Random.nextInt(10, 90)
        val info = TextView(this).apply { text = "Current Number: $currentNum\nWill next number be Higher or Lower?"; setTextColor(Color.WHITE); textSize = 18f }
        layout.addView(info)
        listOf("Higher ⬆️", "Lower ⬇️").forEach { choice ->
            Button(this).apply {
                text = choice
                setOnClickListener {
                    if (!consumeChance(2)) { launchGame(2); return@setOnClickListener }
                    val nextNum = Random.nextInt(1, 100)
                    val isHigher = nextNum > currentNum
                    val won = (choice.startsWith("Higher") && isHigher) || (choice.startsWith("Lower") && !isHigher)
                    Toast.makeText(this@MainActivity, "Next number was $nextNum", Toast.LENGTH_SHORT).show()
                    if (won) addCoins(15)
                    renderGameContent(2)
                }
                layout.addView(this)
            }
        }
    }

    // 4. Rock Paper Scissors
    private fun buildRockPaperScissors(layout: LinearLayout) {
        val moves = listOf("Rock 🪨", "Paper 📄", "Scissors ✂️")
        val info = TextView(this).apply { text = "Choose your move against the Bot:"; setTextColor(Color.WHITE); textSize = 18f }
        layout.addView(info)
        moves.forEachIndexed { userIndex, name ->
            Button(this).apply {
                text = name
                setOnClickListener {
                    if (!consumeChance(3)) { launchGame(3); return@setOnClickListener }
                    val botIndex = Random.nextInt(0, 3)
                    Toast.makeText(this@MainActivity, "Bot chose: ${moves[botIndex]}", Toast.LENGTH_SHORT).show()
                    if (userIndex == botIndex) {
                        Toast.makeText(this@MainActivity, "It's a Draw!", Toast.LENGTH_SHORT).show()
                    } else if ((userIndex == 0 && botIndex == 2) || (userIndex == 1 && botIndex == 0) || (userIndex == 2 && botIndex == 1)) {
                        addCoins(15)
                    } else {
                        Toast.makeText(this@MainActivity, "Bot won!", Toast.LENGTH_SHORT).show()
                    }
                    renderGameContent(3)
                }
                layout.addView(this)
            }
        }
    }

    // 5. Color Stroop Challenge
    private fun buildColorMatch(layout: LinearLayout) {
        val colors = listOf("RED" to Color.RED, "GREEN" to Color.GREEN, "BLUE" to Color.BLUE, "YELLOW" to Color.YELLOW)
        val wordPair = colors.random()
        val textPair = colors.random()
        val isMatching = wordPair.first == textPair.first

        val wordView = TextView(this).apply {
            text = wordPair.first
            setTextColor(textPair.second)
            textSize = 32f
            setPadding(0, 20, 0, 20)
        }
        val prompt = TextView(this).apply { text = "Does the text match the color?"; setTextColor(Color.WHITE); textSize = 16f }
        layout.addView(prompt)
        layout.addView(wordView)

        listOf("YES (Matches)", "NO (Doesn't Match)").forEach { opt ->
            Button(this).apply {
                text = opt
                setOnClickListener {
                    if (!consumeChance(4)) { launchGame(4); return@setOnClickListener }
                    val userSaidYes = opt.startsWith("YES")
                    if (userSaidYes == isMatching) addCoins(15) else Toast.makeText(this@MainActivity, "Wrong!", Toast.LENGTH_SHORT).show()
                    renderGameContent(4)
                }
                layout.addView(this)
            }
        }
    }

    // 6. Odd One Out
    private fun buildOddOneOut(layout: LinearLayout) {
        val emojiSets = listOf(listOf("🐶", "🐶", "🐱", "🐶"), listOf("🍎", "🍎", "🍊", "🍎"), listOf("🚗", "✈️", "🚗", "🚗"))
        val selected = emojiSets.random().shuffled()
        val odd = selected.groupBy { it }.minByOrNull { it.value.size }!!.key

        val prompt = TextView(this).apply { text = "Find the Odd Emoji:"; setTextColor(Color.WHITE); textSize = 18f }
        layout.addView(prompt)

        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selected.forEach { emoji ->
            Button(this).apply {
                text = emoji
                textSize = 28f
                setOnClickListener {
                    if (!consumeChance(5)) { launchGame(5); return@setOnClickListener }
                    if (emoji == odd) addCoins(15) else Toast.makeText(this@MainActivity, "Incorrect!", Toast.LENGTH_SHORT).show()
                    renderGameContent(5)
                }
                grid.addView(this)
            }
        }
        layout.addView(grid)
    }

    // 7. Lucky Dice 7
    private fun buildDice7(layout: LinearLayout) {
        val prompt = TextView(this).apply { text = "Predict total sum of two dice:"; setTextColor(Color.WHITE); textSize = 18f }
        layout.addView(prompt)
        listOf("Under 7 (< 7)", "Lucky 7 (= 7)", "Over 7 (> 7)").forEach { opt ->
            Button(this).apply {
                text = opt
                setOnClickListener {
                    if (!consumeChance(6)) { launchGame(6); return@setOnClickListener }
                    val d1 = Random.nextInt(1, 7)
                    val d2 = Random.nextInt(1, 7)
                    val total = d1 + d2
                    Toast.makeText(this@MainActivity, "Rolled: $d1 + $d2 = $total", Toast.LENGTH_SHORT).show()
                    val won = when {
                        opt.startsWith("Under") -> total < 7
                        opt.star
