package com.claw.passvault.autofill

import android.service.autofill.AutofillService
import android.view.autofill.AutofillValue

/**
 * Stub Autofill service for future implementation.
 * Will provide autofill suggestions for apps and browsers.
 */
class PassVaultAutofillService : AutofillService() {
    override fun onFillRequest(
        request: android.service.autofill.FillRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: android.service.autofill.FillCallback
    ) {
        // TODO: Implement autofill with encrypted vault access
        callback.onSuccess(null)
    }

    override fun onSaveRequest(
        request: android.service.autofill.SaveRequest,
        callback: android.service.autofill.SaveCallback
    ) {
        // TODO: Offer to save new passwords
        callback.onSuccess()
    }
}
