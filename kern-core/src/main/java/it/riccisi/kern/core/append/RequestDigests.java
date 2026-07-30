package it.riccisi.kern.core.append;

import it.riccisi.kern.api.append.AppendRequest;
import it.riccisi.kern.core.storage.RequestDigest;

public interface RequestDigests {
    RequestDigest digest(AppendRequest request);
}
