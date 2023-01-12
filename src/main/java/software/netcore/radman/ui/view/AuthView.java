package software.netcore.radman.ui.view;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import software.netcore.radman.buisness.service.attribute.AttributeService;
import software.netcore.radman.buisness.service.attribute.dto.AttributeDto;
import software.netcore.radman.buisness.service.attribute.dto.AttributeFilter;
import software.netcore.radman.buisness.service.attribute.dto.AuthenticationAttributeDto;
import software.netcore.radman.buisness.service.attribute.dto.AuthorizationAttributeDto;
import software.netcore.radman.buisness.service.auth.AuthService;
import software.netcore.radman.buisness.service.auth.dto.*;
import software.netcore.radman.buisness.service.security.SecurityService;
import software.netcore.radman.buisness.service.user.radius.RadiusUserService;
import software.netcore.radman.buisness.service.user.radius.dto.RadiusGroupDto;
import software.netcore.radman.buisness.service.user.radius.dto.RadiusGroupFilter;
import software.netcore.radman.buisness.service.user.radius.dto.RadiusUserDto;
import software.netcore.radman.buisness.service.user.radius.dto.RadiusUserFilter;
import software.netcore.radman.buisness.service.user.system.dto.RoleDto;
import software.netcore.radman.ui.CreationListener;
import software.netcore.radman.ui.component.ConfirmationDialog;
import software.netcore.radman.ui.converter.AttributeDtoToNameConverter;
import software.netcore.radman.ui.converter.RadiusGroupDtoToNameConverter;
import software.netcore.radman.ui.converter.RadiusUserDtoToNameConverter;
import software.netcore.radman.ui.menu.MenuTemplate;
import software.netcore.radman.ui.notification.ErrorNotification;
import software.netcore.radman.ui.support.Filter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @since v. 1.0.0
 */
@Slf4j
@PageTitle("RadMan: Auth (AA)")
@Route(value = "auth", layout = MenuTemplate.class)
public class AuthView extends VerticalLayout {

    private static final long serialVersionUID = 3514394536491785870L;

    private final AuthService authService;
    private final RadiusUserService userService;
    private final AttributeService attributeService;
    private final SecurityService securityService;

    // columnsList is a class variable that stores the list of table columns for later reference
    private Map<String, String> columnsList;

    @Autowired
    public AuthView(AuthService authService, RadiusUserService userService,
                    AttributeService attributeService, SecurityService securityService) {
        this.authService = authService;
        this.userService = userService;
        this.attributeService = attributeService;
        this.securityService = securityService;
        buildView();
    }

    private void buildView() {
        setSpacing(false);
        add(new H4("Data from Radius DB - \"radcheck\", \"radgroupcheck\", \"radreply\", \"radgroupreply\" tables"));
        add(new AuthenticationGrid());
        add(new AuthorizationGrid());
    }

    private abstract class AuthGrid<T extends AuthsDto, U extends AuthDto> extends Div {

        private final Filter filter = new Filter();
        private final ConfirmationDialog deleteDialog;
        final Grid<Map<String, String>> grid;

        AuthGrid() {
            setWidth("100%");
            RoleDto role = securityService.getLoggedUserRole();
            grid = new Grid<>();
            deleteDialog = new ConfirmationDialog("400px");
            deleteDialog.setTitle("Delete assigned attributes");
            deleteDialog.setConfirmListener(() -> {
                refreshColumnsList();
                Optional<Map<String, String>> optional = grid.getSelectionModel().getFirstSelectedItem();
                optional.ifPresent(row -> {
                    String name = row.get("name");
                    String type = row.get("type");
                    // Since we're deleting single attributes that might have recurrent names but different operations and
                    // values, adding value, operator and attribute forms a more complex composite key that only deletes individual records
                    String value = "";
                    String operator = "";
                    String attribute = "";
                    for (String key : columnsList.keySet()) {
                        // {}, :, name and type are always guaranteed to appear in the array as it stands.  Below statement only
                        // processes the key if it isn't one of said keys or Vaadin crashes
                        if (!key.equals("{}") && !key.equals(":") && !key.equals("type") && !key.equals("name")) {
                            // If row is not empty e.g. contains a value (since there will be a lot of blank columns when the
                            // attributes list increases (and Vaadin returns null for empty columns)
                            if (row.get(key) != null) {
                                // Value is always three characters offset (two for operator, one for a whitespace)
                                value = row.get(key).substring(3);
                                // Operator is always at the start of the string and takes up two characters
                                operator = row.get(key).substring(0,2);
                                // Attribute is already filtered
                                attribute = key;
                            }

                        }
                    }
                    try {
                        // Delete the record with a composite lookup of name, type, attribute, operator and field
                        deleteAssigment(name, type, attribute, operator, value);
                        deleteDialog.setOpened(false);
                        refreshGrid();
                    } catch (Exception e) {
                        log.warn("Failed to delete auth. Reason = '{}'", e.getMessage());
                        ErrorNotification.show("Error",
                                "Ooops, something went wrong, try again please");
                    }
                });
            });

            Button assignBtn = new Button("Assign attribute", event -> getAssigmentDialog().startAssigment());
            assignBtn.setEnabled(role == RoleDto.ADMIN);
            Button deleteBtn = new Button("Delete", event -> {
                Optional<Map<String, String>> optional = grid.getSelectionModel().getFirstSelectedItem();
                optional.ifPresent(row -> {
                    // Make the dialog box more generic now that it supports MV attributes
                    deleteDialog.setDescription("Are you sure you want to delete the selected row?");
                    deleteDialog.setOpened(true);
                });
            });
            deleteBtn.setEnabled(false);

            grid.asSingleSelect().addValueChangeListener(event ->
                    deleteBtn.setEnabled(Objects.nonNull(event.getValue()) && role == RoleDto.ADMIN));

            TextField search = new TextField(event -> {
                filter.setSearchText(event.getValue());
                refreshGrid();
            });
            search.setValueChangeMode(ValueChangeMode.EAGER);
            search.setPlaceholder("Search...");

            HorizontalLayout horizontalLayout = new HorizontalLayout();
            horizontalLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
            horizontalLayout.add(new H3(getGridTitle()));
            horizontalLayout.add(assignBtn);
            horizontalLayout.add(deleteBtn);
            horizontalLayout.add(search);
            add(horizontalLayout);
            add(grid);

            refreshGrid();
        }

        void refreshGrid() {
            AuthsDto authsDto = getAuthsDto(filter);
            if (grid.getColumns().size() == 0) {
                authsDto.getColumnsSpec().keySet().forEach(key
                        -> grid.addColumn((ValueProvider<Map<String, String>, Object>) map
                        -> map.get(key)).setHeader(key));
                grid.getColumns().forEach(column -> column.setSortable(true));
            }
            // During refresh, update the class variable containing the column list for reference later
            columnsList = authsDto.getColumnsSpec();
            grid.setItems(authsDto.getData());
        }

        void refreshColumnsList() {
            AuthsDto authsDto = getAuthsDto(filter);
            columnsList = authsDto.getColumnsSpec();
        }

        abstract String getGridTitle();

        abstract T getAuthsDto(Filter filter);

        abstract AttributeAssignmentDialog<U, ? extends AttributeDto> getAssigmentDialog();

        // Abstract template for row deletion.  Implemented for Authorization and Authentication
        abstract void deleteAssigment(String name, String type, String attribute, String operator, String value);

    }

    private class AuthenticationGrid extends AuthGrid<AuthenticationsDto, AuthenticationDto> {

        private final AuthenticationAttributeAssigmentDialog assigmentDialog;

        AuthenticationGrid() {
            assigmentDialog = new AuthenticationAttributeAssigmentDialog((source, bean) -> refreshGrid());
        }

        @Override
        String getGridTitle() {
            return "Authentication";
        }

        @Override
        AuthenticationsDto getAuthsDto(Filter filter) {
            return authService.getAuthentications(filter);
        }

        @Override
        AttributeAssignmentDialog<AuthenticationDto, ? extends AttributeDto> getAssigmentDialog() {
            return assigmentDialog;
        }

        @Override
        void deleteAssigment(String name, String type, String attribute, String operator, String value) {
            authService.deleteAuthentication(name, type, attribute, operator, value);
        }

    }

    private class AuthorizationGrid extends AuthGrid<AuthorizationsDto, AuthorizationDto> {

        private final AuthorizationAttributeAssigmentDialog assigmentDialog;

        AuthorizationGrid() {
            assigmentDialog = new AuthorizationAttributeAssigmentDialog((source, bean) -> refreshGrid());
        }

        @Override
        String getGridTitle() {
            return "Authorization";
        }

        @Override
        AuthorizationsDto getAuthsDto(Filter filter) {
            return authService.getAuthorizations(filter);
        }

        @Override
        AttributeAssignmentDialog<AuthorizationDto, ? extends AttributeDto> getAssigmentDialog() {
            return assigmentDialog;
        }

        @Override
        void deleteAssigment(String name, String type, String attribute, String operator, String value) {
            authService.deleteAuthorization(name, type, attribute, operator, value);
        }

    }

    private abstract class AttributeAssignmentDialog<T extends AuthDto, U extends AttributeDto> extends Dialog {

        private final CreationListener<Void> creationListener;

        Binder<T> binder;
        private ComboBox<RadiusUserDto> username;
        private ComboBox<RadiusGroupDto> groupName;
        private AbstractSinglePropertyField<? extends AbstractField<?, ?>, String> value;

        AttributeAssignmentDialog(CreationListener<Void> creationListener) {
            this.creationListener = creationListener;
        }

        private void build() {
            removeAll();
            binder = new BeanValidationBinder<>(getClazz());

            HorizontalLayout authTargetConfigLayout = new HorizontalLayout();
            username = new ComboBox<>("Username");
            username.setItemLabelGenerator(RadiusUserDto::getUsername);
            groupName = new ComboBox<>("Group name");
            groupName.setItemLabelGenerator(RadiusGroupDto::getName);
            username.setDataProvider(new CallbackDataProvider<>(query ->
                    userService.pageRadiusUsers(new RadiusUserFilter(query.getFilter().orElse(null),
                            true, false), PageRequest.of(query.getOffset(),
                            query.getLimit(), new Sort(Sort.Direction.ASC, "id"))).stream(),
                    query -> (int) userService.countRadiusUsers(new RadiusUserFilter(query.getFilter()
                            .orElse(null), true, false))));
            groupName.setDataProvider(new CallbackDataProvider<>(query ->
                    userService.pageRadiusUsersGroup(new RadiusGroupFilter(query.getFilter().orElse(null),
                            true, false), PageRequest.of(query.getOffset(),
                            query.getLimit(), new Sort(Sort.Direction.ASC, "id"))).stream(),
                    query -> (int) userService.countRadiusUsersGroup(new RadiusGroupFilter(query.getFilter()
                            .orElse(null), true, false))));

            Select<AuthTarget> authTargetSelect = new Select<>(AuthTarget.values());
            authTargetSelect.setLabel("Type");
            authTargetSelect.setItemLabelGenerator(AuthTarget::getValue);
            authTargetSelect.setTextRenderer(AuthTarget::getValue);
            authTargetSelect.addValueChangeListener(event -> {
                binder.removeBinding("name");
                authTargetConfigLayout.removeAll();
                if (event.getValue() == AuthTarget.RADIUS_USER) {
                    authTargetConfigLayout.add(username);
                    binder.forField(username)
                            .withConverter(new RadiusUserDtoToNameConverter())
                            .bind("name");
                } else {
                    authTargetConfigLayout.add(groupName);
                    binder.forField(groupName)
                            .withConverter(new RadiusGroupDtoToNameConverter())
                            .bind("name");
                }
                authTargetConfigLayout.add(authTargetSelect);
            });
            authTargetSelect.setEmptySelectionAllowed(false);
            authTargetConfigLayout.add(authTargetSelect);

            HorizontalLayout attrConfigLayout = new HorizontalLayout();
            ComboBox<U> attribute = new ComboBox<>("Attribute");
            attribute.setItemLabelGenerator(AttributeDto::getName);
            attribute.setDataProvider((ComboBox.FetchItemsCallback<U>)
                            (searchText, offset, limit) -> pageAttributes(new AttributeFilter(searchText,
                                            true, false),
                                    PageRequest.of(offset, limit, new Sort(Sort.Direction.ASC, "id")))
                                    .stream(),
                    (SerializableFunction<String, Integer>) searchText ->
                            (int) countAttributes(new AttributeFilter(searchText, true,
                                    false)));
            attribute.addValueChangeListener(event -> {
                if (event.getValue() != null && event.getValue().isSensitiveData()) {
                    if (!(value instanceof PasswordField)) {
                        AbstractSinglePropertyField<? extends AbstractField<?, ?>, String> newValueField
                                = buildPasswordValueField();
                        attrConfigLayout.replace(value, newValueField);
                        value = newValueField;
                    }
                } else {
                    if (!(value instanceof TextField)) {
                        AbstractSinglePropertyField<? extends AbstractField<?, ?>, String> newValueField
                                = buildTextValueField();
                        attrConfigLayout.replace(value, newValueField);
                        value = newValueField;
                    }
                }
            });

            Select<RadiusOp> opSelect = new Select<>(RadiusOp.values());
            opSelect.setLabel("Operation");
            opSelect.setItemLabelGenerator(RadiusOp::getValue);
            opSelect.setTextRenderer(RadiusOp::getValue);
            opSelect.setWidth("75px");
            value = buildTextValueField();

            binder.bind(authTargetSelect, "authTarget");
            binder.forField(attribute)
                    .withConverter(new AttributeDtoToNameConverter<>())
                    .bind("attribute");
            binder.bind(opSelect, "op");

            Button assignBtn = new Button("Assign", event -> {
                T dto = getNewBeanInstance();
                if (binder.writeBeanIfValid(dto)) {
                    try {
                        assignAuth(dto);
                        creationListener.onCreated(this, null);
                        setOpened(false);
                    } catch (Exception e) {
                        log.warn("Failed to assign auth. Reason = '{}'", e.getMessage());
                        ErrorNotification.show("Error",
                                "Ooops, something went wrong, try again please");
                    }
                }
            });
            Button cancelBtn = new Button("Cancel", event -> setOpened(false));

            attrConfigLayout.add(attribute, opSelect, value);

            add(new H3(getDialogTitle()));
            add(authTargetConfigLayout);
            add(attrConfigLayout);
            HorizontalLayout controlsLayout = new HorizontalLayout();
            controlsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            controlsLayout.setWidthFull();
            controlsLayout.add(cancelBtn);
            controlsLayout.add(assignBtn);
            add(new Hr());
            add(controlsLayout);
        }

        private AbstractSinglePropertyField<? extends AbstractField<?, ?>, String> buildTextValueField() {
            binder.removeBinding("value");
            TextField valueField = new TextField("Value");
            valueField.setValueChangeMode(ValueChangeMode.EAGER);
            valueField.setClearButtonVisible(false);
            binder.bind(valueField, "value");
            return valueField;
        }

        private AbstractSinglePropertyField<? extends AbstractField<?, ?>, String> buildPasswordValueField() {
            binder.removeBinding("value");
            PasswordField valueField = new PasswordField("Value");
            valueField.setValueChangeMode(ValueChangeMode.EAGER);
            valueField.setClearButtonVisible(false);
            binder.bind(valueField, "value");
            return valueField;
        }

        abstract String getDialogTitle();

        abstract Class<T> getClazz();

        abstract T getNewBeanInstance();

        abstract long countAttributes(AttributeFilter filter);

        abstract Page<U> pageAttributes(AttributeFilter filter, Pageable pageable);

        abstract void assignAuth(T authDto);

        void startAssigment() {
            build();
            setOpened(true);
            T auth = getNewBeanInstance();
            auth.setAuthTarget(AuthTarget.RADIUS_USER);
            binder.readBean(auth);
        }

    }

    private class AuthenticationAttributeAssigmentDialog
            extends AttributeAssignmentDialog<AuthenticationDto, AuthenticationAttributeDto> {

        AuthenticationAttributeAssigmentDialog(CreationListener<Void> creationListener) {
            super(creationListener);
        }

        @Override
        String getDialogTitle() {
            return "Assign authentication attribute";
        }

        @Override
        Class<AuthenticationDto> getClazz() {
            return AuthenticationDto.class;
        }

        @Override
        AuthenticationDto getNewBeanInstance() {
            return new AuthenticationDto();
        }

        @Override
        long countAttributes(AttributeFilter filter) {
            return attributeService.countAuthenticationAttributeRecords(filter);
        }

        @Override
        Page<AuthenticationAttributeDto> pageAttributes(AttributeFilter filter, Pageable pageable) {
            return attributeService.pageAuthenticationAttributeRecords(filter, pageable);
        }

        @Override
        void assignAuth(AuthenticationDto authDto) {
            authService.createAuthentication(authDto);
        }

    }

    private class AuthorizationAttributeAssigmentDialog
            extends AttributeAssignmentDialog<AuthorizationDto, AuthorizationAttributeDto> {

        AuthorizationAttributeAssigmentDialog(CreationListener<Void> creationListener) {
            super(creationListener);
        }

        @Override
        String getDialogTitle() {
            return "Assign authorization attribute";
        }

        @Override
        Class<AuthorizationDto> getClazz() {
            return AuthorizationDto.class;
        }

        @Override
        AuthorizationDto getNewBeanInstance() {
            return new AuthorizationDto();
        }

        @Override
        long countAttributes(AttributeFilter filter) {
            return attributeService.countAuthorizationAttributeRecords(filter);
        }

        @Override
        Page<AuthorizationAttributeDto> pageAttributes(AttributeFilter filter, Pageable pageable) {
            return attributeService.pageAuthorizationAttributeRecords(filter, pageable);
        }

        @Override
        void assignAuth(AuthorizationDto authDto) {
            authService.createAuthorization(authDto);
        }

    }

}
