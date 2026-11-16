package com.starlight.ui.helpers

import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.stringPreferencesKey
import com.starlight.computers.ComputerManagerService
import com.starlight.models.MainViewModel
import com.starlight.nvstream.http.ComputerDetails
import com.starlight.nvstream.http.NvApp
import com.starlight.preferences.PreferenceKeys
import com.starlight.utils.Constants
import com.starlight.utils.Starlight
import com.starlight.utils.Starlight.Companion.isClassicAndroidTV
import com.starlight.utils.Starlight.Companion.isTvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ShortcutHelper(private val context: ComponentActivity) {

    private val tvChannelHelper: TvChannelHelper = TvChannelHelper(context)

    // Get the favorites
    suspend fun getFavorites(name: String) {

        val favorites: List<NvApp> = getArrayList(Constants.FAVORITES_PREF) ?: emptyList()
        val sunFavorites: List<NvApp> = getArrayList(Constants.SUN_FAVORITES_PREF) ?: emptyList()

        if (name != "Sunshine") {
            if (favorites.isNotEmpty()) {
                TvFavAppIds.clear()
                TvFavAppIds.addAll(favorites.toMutableList())
            }
        } else {
            if (sunFavorites.isNotEmpty()) {
                TvSunFavAppIds.clear()
                TvSunFavAppIds.addAll(sunFavorites.toMutableList())
            }
        }
    }

    suspend fun getArrayList(key: String): MutableList<NvApp>? {
        return PreferenceKeys.getArrayList(stringPreferencesKey(key), context)
    }

    // Helper function to report game added to home
    suspend fun reportGameAddedToHome(computer: ComputerDetails?, app: NvApp?) {
        tvChannelHelper.createTvChannel(computer!!)
        tvChannelHelper.addGameToChannel(computer, app!!)
    }


    // Helper function to add game to watch next
    fun addWatchNextShortcut(
        activity: ComponentActivity,
        gameScreenHelper: GameScreenHelper,
        viewModel: MainViewModel,
        computer: ComputerDetails,
        app: NvApp
    ) {
        tvChannelHelper.addGameToWatchNext(computer, app)
        gameScreenHelper.stopConnection()
        if (viewModel.getTVScreenState()) {
            activity.finish()
        } else {
            if (viewModel.isFavs) {
                viewModel.setScreenState("FavoritesScreen")
                viewModel.getNavController()?.popBackStack()
            } else {
                viewModel.setScreenState("MainScreen")
                viewModel.getNavController()?.popBackStack()
            }
        }
    }

    // Helper function to create shortcut
    fun createMainShortcut(computer: ComputerDetails, newlyPaired: Boolean) {
        val isNormalATV = isTvDevice() && isClassicAndroidTV()

        if (newlyPaired) {
            tvChannelHelper.createTvChannel(computer)
            if (isNormalATV) {
                tvChannelHelper.requestChannelOnHomeScreen(computer)
            }
        }
    }

    fun disableComputerShortcut(computer: ComputerDetails) {
        tvChannelHelper.deleteChannel(computer)
    }

    // Helper function to disable app shortcut
    fun disableAppShortcut(computer: ComputerDetails, app: NvApp) {
        tvChannelHelper.deleteProgram(computer, app)
    }

    // Helper function to remove watch next shortcut
    fun removeWatchNextShortcut(app: NvApp) {
        tvChannelHelper.deleteWatchNext(app)
    }

    // Helper function to remove watch next shortcut
    fun removeWatchNextShortcutById(appId: Int) {
        tvChannelHelper.deleteWatchNextById(appId)
    }

    // Helper function to quit game
    fun quitGame(
        activity: ComponentActivity,
        gameScreenHelper: GameScreenHelper,
        computer: ComputerDetails?,
        app: NvApp?,
        managerBinder: ComputerManagerService.ComputerManagerBinder?,
        viewModel: MainViewModel
    ): Job {

        gameScreenHelper.stopConnection()
        viewModel.setPerfOverlay(false)

        return Starlight.applicationScope.launch(Dispatchers.Main) {
            if (viewModel.getIsStopped()) {
                ServerHelper.isAppRunning = false
                ServerHelper.doQuit(
                    activity, computer!!, app!!,
                    managerBinder!!, quitJob(app), viewModel
                )
                if (!viewModel.getTVScreenState()) {
                    if (viewModel.isFavs) {
                        viewModel.setScreenState("FavoritesScreen")
                        viewModel.getNavController()?.popBackStack()
                    } else {
                        viewModel.setScreenState("MainScreen")
                        viewModel.getNavController()?.popBackStack()
                    }
                }
            }
        }
    }


    // Helper function to remove watch next shortcut
    private fun quitJob(selectedApp: NvApp?): Job {
        return Starlight.applicationScope.launch(Dispatchers.Main) {
            removeWatchNextShortcut(
                selectedApp!!
            )
        }
    }

    companion object {
        val TvSunFavAppIds: MutableList<NvApp> = mutableListOf()
        val TvFavAppIds: MutableList<NvApp> = mutableListOf()
    }

}