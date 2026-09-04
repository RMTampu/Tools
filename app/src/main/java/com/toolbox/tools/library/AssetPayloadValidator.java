package com.toolbox.tools.library;

import com.toolbox.tools.core.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AssetPayloadValidator {
    public AssetValidationResult validateReady(
            AssetDescriptor descriptor,
            byte[] payload
    ) {
        List<String> errors = new ArrayList<>();
        if (payload == null || payload.length == 0) {
            errors.add("ASSET_EMPTY");
            return AssetValidationResult.of(errors);
        }
        if (payload.length > descriptor.maxBytes()) {
            errors.add("ASSET_BUDGET_EXCEEDED");
        }
        if (!DigestUtils.sha256(payload).equals(descriptor.sha256())) {
            errors.add("ASSET_SHA256_MISMATCH");
        }
        if (descriptor.consumerIds().isEmpty()) {
            errors.add("ASSET_CONSUMER_UNKNOWN");
        }

        switch (descriptor.kind()) {
            case RAW:
                if (!"application/octet-stream".equals(descriptor.mimeType())) {
                    errors.add("ASSET_MIME_MISMATCH");
                }
                break;
            case JSON:
            case TEMPLATE_DATA:
                if (!"application/json".equals(descriptor.mimeType())) {
                    errors.add("ASSET_MIME_MISMATCH");
                }
                String text = new String(payload, StandardCharsets.UTF_8);
                if (!JsonSyntaxValidator.isValid(text)) {
                    errors.add("ASSET_JSON_INVALID");
                }
                break;
            case IMAGE:
            case ICON:
            case FONT:
            case AUDIO:
            case VIDEO:
                errors.add("ASSET_RUNTIME_TYPE_VALIDATOR_REQUIRED");
                break;
            default:
                errors.add("ASSET_TYPE_UNSUPPORTED");
        }
        return AssetValidationResult.of(errors);
    }
}
