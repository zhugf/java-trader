package trader.service.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Entity
@Table(name = "all_playbooks")
public class JPAPlaybookEntity extends AbsJPAEntity {

    @Column(name = "groupId", columnDefinition="VARCHAR(64)" )
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

    public JsonElement toJson() {
        JsonObject json =(JsonObject) super.toJson();
        json.addProperty("groupId", getGroupId());
        return json;
    }

}
