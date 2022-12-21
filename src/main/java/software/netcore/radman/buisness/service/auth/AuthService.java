package software.netcore.radman.buisness.service.auth;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.ConversionService;
import software.netcore.radman.buisness.service.auth.dto.*;
import software.netcore.radman.data.internal.entity.RadCheckAttribute;
import software.netcore.radman.data.internal.entity.RadReplyAttribute;
import software.netcore.radman.data.internal.repo.RadCheckAttributeRepo;
import software.netcore.radman.data.internal.repo.RadReplyAttributeRepo;
import software.netcore.radman.data.radius.entity.RadCheck;
import software.netcore.radman.data.radius.entity.RadGroupCheck;
import software.netcore.radman.data.radius.entity.RadGroupReply;
import software.netcore.radman.data.radius.entity.RadReply;
import software.netcore.radman.data.radius.repo.RadCheckRepo;
import software.netcore.radman.data.radius.repo.RadGroupCheckRepo;
import software.netcore.radman.data.radius.repo.RadGroupReplyRepo;
import software.netcore.radman.data.radius.repo.RadReplyRepo;
import software.netcore.radman.ui.support.Filter;

import java.util.*;

/**
 * @since v. 1.0.0
 */
@RequiredArgsConstructor
public class AuthService {

    private static final String NAME_COLUMN_KEY = "name";
    private static final String TYPE_COLUMN_KEY = "type";

    // radius
    private final RadCheckRepo radCheckRepo;
    private final RadReplyRepo radReplyRepo;
    private final RadGroupCheckRepo radGroupCheckRepo;
    private final RadGroupReplyRepo radGroupReplyRepo;

    // internal
    private final RadCheckAttributeRepo radCheckAttributeRepo;
    private final RadReplyAttributeRepo radReplyAttributeRepo;

    private final ConversionService conversionService;

    public void createAuthentication(AuthenticationDto authenticationDto) {
        if (authenticationDto.getAuthTarget() == AuthTarget.RADIUS_USER) {
            RadCheck radCheck = conversionService.convert(authenticationDto, RadCheck.class);
            // Only delete individual records - in this case, ID is arbitrary but it serves to separate from other records with the same username and attribute
            radCheckRepo.deleteByIdAndUsernameAndAttribute(radCheck.getId(), radCheck.getUsername(), radCheck.getAttribute());
            radCheckRepo.save(radCheck);
        } else {
            RadGroupCheck radGroupCheck = conversionService.convert(authenticationDto, RadGroupCheck.class);
            radGroupCheckRepo.deleteAllByIdAndGroupNameAndAttribute(radGroupCheck.getId(), radGroupCheck.getGroupName(),
                    radGroupCheck.getAttribute());
            radGroupCheckRepo.save(radGroupCheck);
        }
    }

    public void createAuthorization(AuthorizationDto authorizationDto) {
        if (authorizationDto.getAuthTarget() == AuthTarget.RADIUS_USER) {
            RadReply radReply = conversionService.convert(authorizationDto, RadReply.class);
            radReplyRepo.deleteByIdAndUsernameAndAttribute(radReply.getId(), radReply.getUsername(), radReply.getAttribute());
            radReplyRepo.save(radReply);
        } else {
            RadGroupReply radGroupReply = conversionService.convert(authorizationDto, RadGroupReply.class);
            radGroupReplyRepo.deleteAllByIdAndGroupNameAndAttribute(radGroupReply.getId(), radGroupReply.getGroupName(),
                    radGroupReply.getAttribute());
            radGroupReplyRepo.save(radGroupReply);
        }
    }

    public void deleteAuthentication(String name, String type, String attribute, String operator, String value) {
        AuthTarget authTarget = AuthTarget.fromValue(type);
        if (authTarget == AuthTarget.RADIUS_USER) {
            // When delete is called on an individual record ONLY delete that one (won't delete all multivalued attributes)
            radCheckRepo.deleteByUsernameAndAttributeAndOpAndValue(name, attribute, operator, value);
        } else {
            radGroupCheckRepo.deleteByGroupNameAndAttributeAndOpAndValue(name, attribute, operator, value);
        }
    }

    public void deleteAuthorization(String name, String type, String attribute, String operator, String value) {
        AuthTarget authTarget = AuthTarget.fromValue(type);
        if (authTarget == AuthTarget.RADIUS_USER) {
            radReplyRepo.deleteAllByUsernameAndAttributeAndOpAndValue(name, attribute, operator, value);
        } else {
            radGroupReplyRepo.deleteByGroupNameAndAttributeAndOpAndValue(name, attribute, operator, value);
        }
    }

    /**
     * AuthorizationDto is a class that contains a Map and a List of Maps
     * @param filter (Literally just a class containing a string)
     * @return
     */
    @SuppressWarnings("Duplicates")
    public AuthorizationsDto getAuthorizations(Filter filter) {
        // Initialize Map containing name: Name and type: Type
        Map<String, String> columnsSpec = initCommonColumnsSpec();

        // Return all rows in the "radreply" table
        List<RadReply> radReplies = radReplyRepo.findAll();
        // Init empty HashMap
        Map<String, List<RadReply>> radReplyMap = new HashMap<>();
        // For all rows in "radreply"
        for (RadReply radReply : radReplies) {
            // Add the current iterations attribute as the key and a new ArrayList as the value if the key doesn't exist
            radReplyMap.putIfAbsent(radReply.getAttribute(), new ArrayList<>());
            // Retrieve the previously added entry and add the current radreply to the List of said entry
            radReplyMap.get(radReply.getAttribute()).add(radReply);
        }

        // Do exactly the same as the above, except for the "radgroupreply" table
        List<RadGroupReply> radGroupReplies = radGroupReplyRepo.findAll();
        Map<String, List<RadGroupReply>> radGroupReplyMap = new HashMap<>();
        for (RadGroupReply radGroupReply : radGroupReplies) {
            radGroupReplyMap.putIfAbsent(radGroupReply.getAttribute(), new ArrayList<>());
            radGroupReplyMap.get(radGroupReply.getAttribute()).add(radGroupReply);
        }

        // Declare two new HashMaps that store a string as the key and a Map<String, String> as the value
        Map<String, Map<String, String>> usersData = new HashMap<>();
        Map<String, Map<String, String>> groupsData = new HashMap<>();

        // Retrieve all rows from the "radreply_attribute" table (Radman)
        List<RadReplyAttribute> radReplyAttributes = radReplyAttributeRepo.findAll();
        // Declare empty HashMap with key: String, value: RadReplyAttribute
        Map<String, RadReplyAttribute> repliesAttrMapping = new HashMap<>();
        // For every row in "radreply_attribute"
        for (RadReplyAttribute radReplyAttribute : radReplyAttributes) {
            // Get name column of current row and place it as the key, place entire row as value
            repliesAttrMapping.put(radReplyAttribute.getName(), radReplyAttribute);
            // In columnsSpec, place the name of the attribute as key and the same but capitalized as the key
            columnsSpec.put(radReplyAttribute.getName(), StringUtils.capitalize(radReplyAttribute.getName()));
            // If radReplyMap contains the key of the current attributes name
            if (radReplyMap.containsKey(radReplyAttribute.getName())) {
                // Get the list of rows in "radreply" that are stored under the key for the current reply attribute
                // and store in a new list of RadReply's
                List<RadReply> attrRadReplies = radReplyMap.get(radReplyAttribute.getName());
                // For every entry in the previously populated List of "radreply's" for a given attribute
                for (RadReply attrRadReply : attrRadReplies) {
                    // Retrieve the name and type in a Map from the current row (or set)
                    Map<String, String> singleUserData = initDefaultRowDataIfRequired(attrRadReply.getUsername(),
                            AuthTarget.RADIUS_USER.getValue(), usersData);
                    // If current attribute is sensitive, censor it
                    String attrValue = radReplyAttribute.isSensitiveData() ?
                            attrRadReply.getValue().replaceAll(".", "*") : attrRadReply.getValue();
                    // Merge the current row's operator and value in a string
                    String finalValue = attrRadReply.getOp() + " " + attrValue;
                    // Put attribute name as key and finalValue as value in singleUserData.  This is where the overwriting is happening
                    singleUserData.put(radReplyAttribute.getName(), finalValue);
                    // singleUserData might (see: does) back-propagate back up to usersData since it's returned from initDefaultRowData
                    // and referred maps seem to back-propagate?
                }
            }

            // Same as above, but for groups
            if (radGroupReplyMap.containsKey(radReplyAttribute.getName())) {
                List<RadGroupReply> attrRadGroupReplies = radGroupReplyMap.get(radReplyAttribute.getName());
                for (RadGroupReply attrRadGroupReply : attrRadGroupReplies) {
                    Map<String, String> singleGroupData = initDefaultRowDataIfRequired(attrRadGroupReply.getGroupName(),
                            AuthTarget.RADIUS_GROUP.getValue(), groupsData);
                    String attrValue = radReplyAttribute.isSensitiveData() ?
                            attrRadGroupReply.getValue().replaceAll(".", "*")
                            : attrRadGroupReply.getValue();
                    singleGroupData.put(radReplyAttribute.getName(), attrRadGroupReply.getOp() + " " + attrValue);
                }
            }
        }

        List<Map<String, String>> data = new ArrayList<>();
        data.addAll(usersData.values());
        data.addAll(groupsData.values());

        // apply filter
        if (!StringUtils.isEmpty(filter.getSearchText())) {
            Iterator<Map<String, String>> iterator = data.iterator();
            while (iterator.hasNext()) {
                Map<String, String> row = iterator.next();
                boolean pass = false;
                for (String key : row.keySet()) {
                    if (Objects.equals(NAME_COLUMN_KEY, key) || Objects.equals(TYPE_COLUMN_KEY, key)) {
                        String opValue = row.get(key);
                        if (Objects.nonNull(opValue)) {
                            pass = pass || StringUtils.contains(opValue, filter.getSearchText());
                        }
                    } else {
                        RadReplyAttribute radReplyAttribute = repliesAttrMapping.get(key);
                        if (!radReplyAttribute.isSensitiveData()) {
                            String opValue = row.get(key);
                            if (Objects.nonNull(opValue)) {
                                pass = pass || StringUtils.contains(opValue, filter.getSearchText());
                            }
                        }
                    }
                }
                if (!pass) {
                    iterator.remove();
                }
            }
        }

        return new AuthorizationsDto(columnsSpec, data);
    }


    @SuppressWarnings("Duplicates")
    public AuthenticationsDto getAuthentications(Filter filter) {
        Map<String, String> columnsSpec = initCommonColumnsSpec();

        List<RadCheck> radChecks = radCheckRepo.findAll();
        Map<String, List<RadCheck>> radCheckMap = new HashMap<>();
        for (RadCheck radCheck : radChecks) {
            radCheckMap.putIfAbsent(radCheck.getAttribute(), new ArrayList<>());
            radCheckMap.get(radCheck.getAttribute()).add(radCheck);
        }

        List<RadGroupCheck> radGroupChecks = radGroupCheckRepo.findAll();
        Map<String, List<RadGroupCheck>> radGroupCheckMap = new HashMap<>();
        for (RadGroupCheck radGroupCheck : radGroupChecks) {
            radGroupCheckMap.putIfAbsent(radGroupCheck.getAttribute(), new ArrayList<>());
            radGroupCheckMap.get(radGroupCheck.getAttribute()).add(radGroupCheck);
        }

        Map<String, Map<String, String>> usersData = new HashMap<>();
        Map<String, Map<String, String>> groupsData = new HashMap<>();

        List<RadCheckAttribute> radCheckAttributes = radCheckAttributeRepo.findAll();
        Map<String, RadCheckAttribute> checksAttrMapping = new HashMap<>();
        for (RadCheckAttribute radCheckAttribute : radCheckAttributes) {
            checksAttrMapping.put(radCheckAttribute.getName(), radCheckAttribute);
            columnsSpec.put(radCheckAttribute.getName(), StringUtils.capitalize(radCheckAttribute.getName()));

            if (radCheckMap.containsKey(radCheckAttribute.getName())) {
                List<RadCheck> attrRadChecks = radCheckMap.get(radCheckAttribute.getName());
                for (RadCheck attrRadCheck : attrRadChecks) {
                    Map<String, String> singleUserData = initDefaultRowDataIfRequired(attrRadCheck.getUsername(),
                            AuthTarget.RADIUS_USER.getValue(), usersData);
                    String attrValue = radCheckAttribute.isSensitiveData() ?
                            attrRadCheck.getValue().replaceAll(".", "*") :
                            attrRadCheck.getValue();
                    singleUserData.put(radCheckAttribute.getName(), attrRadCheck.getOp() + " " + attrValue);
                }
            }

            if (radGroupCheckMap.containsKey(radCheckAttribute.getName())) {
                List<RadGroupCheck> attrRadGroupChecks = radGroupCheckMap.get(radCheckAttribute.getName());
                for (RadGroupCheck attrRadGroupCheck : attrRadGroupChecks) {
                    Map<String, String> singleGroupData = initDefaultRowDataIfRequired(attrRadGroupCheck.getGroupName(),
                            AuthTarget.RADIUS_GROUP.getValue(), groupsData);
                    String attrValue = radCheckAttribute.isSensitiveData() ?
                            attrRadGroupCheck.getValue().replaceAll(".", "*")
                            : attrRadGroupCheck.getValue();
                    singleGroupData.put(radCheckAttribute.getName(), attrRadGroupCheck.getOp() + " " + attrValue);
                }
            }
        }

        List<Map<String, String>> data = new ArrayList<>();
        data.addAll(usersData.values());
        data.addAll(groupsData.values());

        // apply filter
        if (!StringUtils.isEmpty(filter.getSearchText())) {
            Iterator<Map<String, String>> iterator = data.iterator();
            while (iterator.hasNext()) {
                Map<String, String> row = iterator.next();
                boolean pass = false;
                for (String key : row.keySet()) {
                    if (Objects.equals(NAME_COLUMN_KEY, key) || Objects.equals(TYPE_COLUMN_KEY, key)) {
                        String opValue = row.get(key);
                        if (Objects.nonNull(opValue)) {
                            pass = pass || StringUtils.contains(opValue, filter.getSearchText());
                        }
                    } else {
                        RadCheckAttribute radReplyAttribute = checksAttrMapping.get(key);
                        if (!radReplyAttribute.isSensitiveData()) {
                            String opValue = row.get(key);
                            if (Objects.nonNull(opValue)) {
                                pass = pass || StringUtils.contains(opValue, filter.getSearchText());
                            }
                        }
                    }
                }
                if (!pass) {
                    iterator.remove();
                }
            }
        }

        return new AuthenticationsDto(columnsSpec, data);
    }

    /**
     *
     * Create HashMap (K:V Pair) with name and type as column key.  HashMap where each entry is part of a doubly-
     * linked-list - allows linear traversal
     * @return LinkedHashMap<String, String>
     */

    private Map<String, String> initCommonColumnsSpec() {
        Map<String, String> columnsSpec = new LinkedHashMap<>();
        columnsSpec.put(NAME_COLUMN_KEY, "Name");
        columnsSpec.put(TYPE_COLUMN_KEY, "Type");
        return columnsSpec;
    }

    /**
     * Takes a name, user type (user or group) and a Map with key: Map and value: Map<String, String>.  If
     * combination of name and user doesn't exist in the provided Map, initialize an entry and return a
     * Map<String, String> with K:V as name:<provided name> and type:<provided type>
     * @param name
     * @param type
     * @param data
     * @return Map<String, String>
     */
    private static int row_counter = 0;
    private Map<String, String> initDefaultRowDataIfRequired(String name, String type,
                                                             Map<String, Map<String, String>> data) {
        // Set key to a combination of the name (normally username) and user type (either user or group) and, per my addition,
        // the `row_counter` variable.  This stops multivalued attributes being added to the same row which breaks Vaadin and instead
        // pushes them to a separate row.  Yes it's hacky and the if statement could be removed, might refactor but it's perfectly performant
        // for it's purpose
        String key = name + ":" + type + ":" + row_counter;
        row_counter++;
        // Create an object of type Map
        Map<String, String> singleData;
        // If the referred data doesn't contain the above key
        if (!data.containsKey(key)) {
            // Initialize previously declared Map as HashMap
            singleData = new HashMap<>();
            // Put key and empty HashMap into referred data
            data.put(key, singleData);
            //  Add name and type to initialized HashMap, Java references are weird.  Post-addition seems to propagate
            //  to all referees
            singleData.put(NAME_COLUMN_KEY, name);
            singleData.put(TYPE_COLUMN_KEY, type);
        } else {
            // If key exists, return existing data from referred data
            singleData = data.get(key);
        }
        return singleData;
    }

}
