/*
 * Copyright 2017 Anton Bulakh <necauqua@gmail.com>
 * Licensed under MIT, see LICENSE file for details.
 */

package info.necauqua.mods.subpocket.api;

import javax.annotation.Nonnull;

public class SubpocketAPI {

    /**
     * Set at mods pre-init stage.
     * This value is also a mod instance.
     **/
    public static ISubpocketAPI instance = null;

    public interface ISubpocketAPI {

        /**
          * @return IPositionedBigStackFactory implementation.
          **/
        @Nonnull
        IPositionedBigStackFactory getStackFactory();
    }
}
