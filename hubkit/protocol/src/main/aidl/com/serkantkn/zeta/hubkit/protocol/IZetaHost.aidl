package com.serkantkn.zeta.hubkit.protocol;

import android.os.Bundle;
import com.serkantkn.zeta.hubkit.protocol.HubNode;

/** What a hub app may tell Zeta. One way only: neither side ever waits on the other. */
oneway interface IZetaHost {
    /** The hub as it stands now, whole. */
    void spec(in HubNode spec);

    /** Something for Zeta to do: show a note, open a page on the web, share. See HubProtocol.Command. */
    void command(in Bundle command);
}
