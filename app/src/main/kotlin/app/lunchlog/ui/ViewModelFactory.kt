package app.lunchlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.lunchlog.ServiceLocator
import app.lunchlog.ui.auth.SignInViewModel
import app.lunchlog.ui.detail.RecordDetailViewModel
import app.lunchlog.ui.edit.RecordEditViewModel
import app.lunchlog.ui.home.HomeViewModel

/**
 * ViewModel の生成。DI ライブラリを入れない代わりの最小限の仕掛け。
 */
class ViewModelFactory(
    private val locator: ServiceLocator,
    private val recordId: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SignInViewModel::class.java) ->
            SignInViewModel(locator.auth) as T

        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(locator.recordRepository) as T

        modelClass.isAssignableFrom(RecordEditViewModel::class.java) ->
            RecordEditViewModel(locator.recordRepository, locator.currentLocation) as T

        modelClass.isAssignableFrom(RecordDetailViewModel::class.java) ->
            RecordDetailViewModel(locator.recordRepository, requireNotNull(recordId)) as T

        else -> error("未知の ViewModel: ${modelClass.name}")
    }
}
