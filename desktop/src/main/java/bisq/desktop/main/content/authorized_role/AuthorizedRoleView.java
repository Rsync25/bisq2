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

package bisq.desktop.main.content.authorized_role;

import bisq.bonded_roles.BondedRoleType;
import bisq.desktop.common.view.Navigation;
import bisq.desktop.common.view.NavigationTarget;
import bisq.desktop.common.view.TabButton;
import bisq.desktop.main.content.ContentTabView;
import bisq.i18n.Res;
import javafx.collections.ListChangeListener;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AuthorizedRoleView extends ContentTabView<AuthorizedRoleModel, AuthorizedRoleController> {
    private final Map<BondedRoleType, TabButton> tabButtonByBondedRoleType = new HashMap<>();
    private final ListChangeListener<BondedRoleType> listener;

    public AuthorizedRoleView(AuthorizedRoleModel model, AuthorizedRoleController controller) {
        super(model, controller);

        model.getBondedRoleTypes().forEach(bondedRoleType -> {
            String name = bondedRoleType.name();
            TabButton tabButton = addTab(Res.get("authorizedRole." + name), NavigationTarget.valueOf(name));
            tabButtonByBondedRoleType.put(bondedRoleType, tabButton);
        });

        listener = c -> updateVisibility();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();

        model.getAuthorizedBondedRoles().addListener(listener);
        updateVisibility();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();

        model.getAuthorizedBondedRoles().removeListener(listener);
    }

    private void updateVisibility() {
        tabButtonByBondedRoleType.forEach((bondedRoleType, tabButton) -> {
            boolean isVisible = model.getAuthorizedBondedRoles().contains(bondedRoleType);
            tabButton.setVisible(isVisible);
            tabButton.setManaged(isVisible);

            if (isVisible) {
                model.getSelectedTabButton().set(tabButton);
            }
        });

        for (BondedRoleType bondedRoleType : model.getBondedRoleTypes()) {
            TabButton tabButton = tabButtonByBondedRoleType.get(bondedRoleType);
            if (tabButton.isVisible()) {
                model.getSelectedTabButton().set(tabButton);
                break;
            }
        }
        Navigation.navigateTo(model.getSelectedTabButton().get().getNavigationTarget());
    }
}
