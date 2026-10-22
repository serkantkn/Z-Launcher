package com.serkantkn.zeta.hubkit.protocol;

import android.os.Bundle;
import com.serkantkn.zeta.hubkit.protocol.IZetaHost;

/** What Zeta may tell a hub app: the service behind com.serkantkn.zeta.action.HUB returns this. */
oneway interface IZetaHub {
    /** Zeta has opened the hub: who it is (HubProtocol.Host) and where to send the hub. */
    void hello(in Bundle host, IZetaHost callback);

    /** Something happened on the hub: a touch, a switch, a page turned. See HubProtocol.Event. */
    void event(in Bundle event);

    /** Zeta has let the hub go. */
    void bye();
}
