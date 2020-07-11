package trader.service.repository.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Entity
@Table(name = "ALL_PLAYBOOKS")
public class JPAPlaybookEntity extends AbsJPAEntity {

    @Column(name = "GROUP_ID", columnDefinition="VARCHAR(64)" )
    protected String groupId;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setAttrs(JsonElement json) {
        super.setAttrs(json);
        JsonObject json2 = json.getAsJsonObject();
        if ( json2.has("groupId")) {
            groupId = json2.get("groupId").getAsString();
        }
    }

}
