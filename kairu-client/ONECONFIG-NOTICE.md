# OneConfig source notice

This client vendors the OneConfig v1 source at `oneconfig-source/` and builds its
Fabric 26.2 bootstrap into the Kairu client as a nested Fabric JAR. It is present so
players install **only** `KairuSmpClient-0.5.0.jar`.

Pinned upstream source: `Polyfrost/OneConfig` commit
`e8c55db99c847e1fa57861aa98242a8ab812fd32`.

OneConfig is copyright Polyfrost Inc. and contributors and is licensed under GNU
LGPLv3 **and** the Additional Terms Applicable to OneConfig. The complete upstream
license is retained unchanged at `oneconfig-source/LICENSE` and must stay with any
source distribution of this client.

Kairu server controls remain server-authoritative: UI interaction sends a Kairu
request and SMPPlatform performs permission checks and the actual mutation.
