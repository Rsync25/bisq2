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

package bisq.desktop.main.content.user.password;

import bisq.desktop.ServiceProvider;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.desktop.components.overlay.Popup;
import bisq.i18n.Res;
import bisq.user.identity.UserIdentityService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// TODO if walletService is present we want to encrypt the wallet file.
// Wallet encryption is not implemented yet in the wallet domain
@Slf4j
public class PasswordController implements Controller {
    @Getter
    private final PasswordView view;
    private final PasswordModel model;
    private final UserIdentityService userIdentityService;

    public PasswordController(ServiceProvider serviceProvider) {
        userIdentityService = serviceProvider.getUserService().getUserIdentityService();
        model = new PasswordModel();
        view = new PasswordView(model, this);
    }

    @Override
    public void onActivate() {
        doActivate();
    }

    @Override
    public void onDeactivate() {
        doDeactivate();
    }

    void onButtonClicked() {
        CharSequence password = model.getPassword().get();

        if (userIdentityService.getAESSecretKey().isPresent()) {
            if (model.getPasswordIsValid().get()) {
                removePassword(password);
                view.resetValidations();
            }
        } else {
            if (model.getPasswordIsValid().get() && model.getConfirmedPasswordIsValid().get()) {
                setPassword(password);
                view.resetValidations();
            }
        }
    }

    private void removePassword(CharSequence password) {
        userIdentityService.removePassword(password).whenComplete((nil2, throwable) -> {
            maybeHandleError(throwable);
            if (throwable == null) {
                UIThread.run(() -> {
                    new Popup().feedback(Res.get("user.password.removePassword.success")).show();
                    doDeactivate();
                    doActivate();
                });
            }
        });
    }

    private void setPassword(CharSequence password) {
        userIdentityService.deriveKeyFromPassword(password)
                .whenComplete((key, throwable) -> maybeHandleError(throwable))
                .thenCompose(key -> userIdentityService.encryptDataStore())
                .whenComplete((encryptedData, throwable) -> {
                    maybeHandleError(throwable);
                    if (throwable == null) {
                        UIThread.run(() -> {
                            new Popup().feedback(Res.get("user.password.savePassword.success")).show();
                            doDeactivate();
                            doActivate();
                        });
                    }
                });
    }

    private void maybeHandleError(@Nullable Throwable throwable) {
        if (throwable != null) {
            UIThread.run(() -> {
                new Popup().error(throwable).show();
                doDeactivate();
                doActivate();
            });
        }
    }

    private void doActivate() {
        model.getPasswordIsMasked().set(true);
        model.getConfirmedPasswordIsMasked().set(true);
        model.getPassword().set("");
        model.getConfirmedPassword().set("");

        boolean isKeyPresent = userIdentityService.getAESSecretKey().isPresent();
        model.getConfirmedPasswordVisible().set(!isKeyPresent);
        if (isKeyPresent) {
            model.getHeadline().set(Res.get("user.password.headline.removePassword"));
            model.getButtonText().set(Res.get("user.password.button.removePassword"));
        } else {
            model.getHeadline().set(Res.get("user.password.headline.setPassword"));
            model.getButtonText().set(Res.get("user.password.button.savePassword"));
        }
    }

    private void doDeactivate() {
        model.getPassword().set("");
        model.getConfirmedPassword().set("");
    }
}
