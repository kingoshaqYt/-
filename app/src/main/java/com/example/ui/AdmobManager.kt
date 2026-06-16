package com.example.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

object AdmobManager {
    private const val TAG = "AdmobManager"

    // Real Ad Unit IDs provided by the user
    const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/5354046379"

    private var mInterstitialAd: InterstitialAd? = null
    private var mRewardedAd: RewardedAd? = null
    private var mRewardedInterstitialAd: RewardedInterstitialAd? = null

    // 1. BANNER AD COMPOSABLE
    @Composable
    fun AdmobBanner(modifier: Modifier = Modifier) {
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
        )
    }

    // 2. INTERSTITIAL LOAD AND SHOW
    fun loadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Interstitial fail: ${adError.message}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Interstitial loaded")
                mInterstitialAd = interstitialAd
            }
        })
    }

    fun showInterstitial(activity: Activity, onDismiss: () -> Unit) {
        val ad = mInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial dismissed")
                    mInterstitialAd = null
                    loadInterstitial(activity) // Preload next
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.d(TAG, "Interstitial fail show: ${adError.message}")
                    mInterstitialAd = null
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial not ready")
            Toast.makeText(activity, "Loading security ad panel...", Toast.LENGTH_SHORT).show()
            loadInterstitial(activity)
            onDismiss()
        }
    }

    // 3. REWARDED LOAD AND SHOW
    fun loadRewardedAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Rewarded ad load fail: ${adError.message}")
                mRewardedAd = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d(TAG, "Rewarded ad loaded successfully")
                mRewardedAd = rewardedAd
            }
        })
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: (Int) -> Unit) {
        val ad = mRewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    mRewardedAd = null
                    loadRewardedAd(activity) // Preload next
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.d(TAG, "Rewarded show fail: ${adError.message}")
                    mRewardedAd = null
                }
            }
            ad.show(activity) { reward ->
                Log.d(TAG, "User earned credit reward: ${reward.amount}")
                onRewardEarned(reward.amount)
            }
        } else {
            Log.d(TAG, "Rewarded ad was not loaded yet.")
            Toast.makeText(activity, "Retrieving premium reward feed, please try again in a few seconds.", Toast.LENGTH_LONG).show()
            loadRewardedAd(activity)
        }
    }

    // 4. REWARDED INTERSTITIAL
    fun loadRewardedInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(context, REWARDED_INTERSTITIAL_ID, adRequest, object : RewardedInterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d(TAG, "Rewarded Interstitial fail: ${loadAdError.message}")
                mRewardedInterstitialAd = null
            }

            override fun onAdLoaded(rewardedInterstitialAd: RewardedInterstitialAd) {
                Log.d(TAG, "Rewarded Interstitial loaded")
                mRewardedInterstitialAd = rewardedInterstitialAd
            }
        })
    }

    fun showRewardedInterstitial(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = mRewardedInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded Interstitial dismissed")
                    mRewardedInterstitialAd = null
                    loadRewardedInterstitial(activity) // Preload next
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.d(TAG, "Rewarded Interstitial show fail: ${adError.message}")
                    mRewardedInterstitialAd = null
                }
            }
            ad.show(activity) {
                onRewardEarned()
            }
        } else {
            Log.d(TAG, "Rewarded Interstitial not ready")
            Toast.makeText(activity, "Preloading priority channel...", Toast.LENGTH_SHORT).show()
            loadRewardedInterstitial(activity)
        }
    }
}
