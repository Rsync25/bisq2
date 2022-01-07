/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.common.view;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.stage.Window;

public abstract class View<R extends Node, M extends Model, C extends Controller> {
    protected final R root;
    protected final M model;
    protected final C controller;

    public View(R root, M model, C controller) {
        this.root = root;
        this.model = model;
        this.controller = controller;
        if (root != null) {
            root.sceneProperty().addListener((ov, oldValue, newValue) -> {
                if (oldValue == null && newValue != null) {
                    if (newValue.getWindow() != null) {
                        activate(); // activate view first as it usually sets the bindings here
                        controller.activate();
                        model.activate();
                    } else {
                        // For overlays, we need to wait until stage is available
                        newValue.windowProperty().addListener(new ChangeListener<Window>() {
                            @Override
                            public void changed(ObservableValue<? extends Window> observable, Window oldValue, Window newValue) {
                                if (newValue != null) {
                                    activate();
                                    controller.activate();
                                    model.activate();
                                }
                            }
                        });
                    }
                } else if (oldValue != null && newValue == null) {
                    deactivate();
                    controller.deactivate();
                    model.deactivate();
                }
            });
        }
    }

    public R getRoot() {
        return root;
    }

    protected void activate() {
    }

    protected void deactivate() {
    }
}