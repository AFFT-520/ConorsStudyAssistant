import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf // For the list of steps
import com.conor.quizzer.TutorialStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow // To expose as immutable StateFlow

class TutorialViewModel : ViewModel() {

    private val _tutorialSteps = mutableStateListOf<TutorialStep>()
    val tutorialSteps: List<TutorialStep> = _tutorialSteps

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _isTutorialActive = MutableStateFlow(false)
    val isTutorialActive: StateFlow<Boolean> = _isTutorialActive.asStateFlow()

    private val _isTutorialFinished = MutableStateFlow(false)
    val isTutorialFinished: StateFlow<Boolean> = _isTutorialFinished.asStateFlow()

    fun initializeTutorial(steps: List<TutorialStep>) {
        _tutorialSteps.clear()
        _tutorialSteps.addAll(steps)
        _currentStepIndex.value = 0 // For StateFlow, use .value to set
        if (steps.isNotEmpty()) {
            _isTutorialActive.value = true
        } else {
            _isTutorialActive.value = false // Ensure it's false if no steps
        }
    }

    fun nextStep() {
        if (_currentStepIndex.value < _tutorialSteps.size - 1) {
            _currentStepIndex.value++
        } else {
            finishTutorial() // Last step completed
        }
    }

    fun skipTutorial() {
        finishTutorial()
    }

    private fun finishTutorial() {
        _isTutorialActive.value = false
        _isTutorialFinished.value = true
        // Consider what _currentStepIndex should be after finishing
        // _currentStepIndex.value = 0 // Reset if needed
        // Persist that the tutorial has been seen (e.g., using AppPreferences)
        // This persistence logic might live outside the ViewModel or be triggered by an event.
    }

    // This getter logic is fine, but ensure its dependencies are correct
    val currentTutorialStep: TutorialStep?
        get() = if (isTutorialActive.value && // .value for StateFlow
            _tutorialSteps.isNotEmpty() &&
            _currentStepIndex.value >= 0 && _currentStepIndex.value < _tutorialSteps.size) {
            _tutorialSteps.getOrNull(_currentStepIndex.value)
        } else {
            null
        }
}

// Assuming TutorialStep data class is defined elsewhere or in this file
// data class TutorialStep(val id: String, val text: String, val targetCoordinates: Rect?)
