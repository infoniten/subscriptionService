package com.example.subscription.service.validation.metamodel;

import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.exception.ApiException;

/**
 * Raised when the metamodel cannot be loaded from DataDictionary. At startup this fails the
 * application (fail-fast). If it is ever raised while serving a request, it surfaces as 503.
 */
public class MetamodelUnavailableException extends ApiException {

    public MetamodelUnavailableException(String message, Throwable cause) {
        super(ErrorCode.METAMODEL_UNAVAILABLE, "Metamodel unavailable: " + message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
