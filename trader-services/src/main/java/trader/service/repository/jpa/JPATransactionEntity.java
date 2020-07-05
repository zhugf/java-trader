package trader.service.repository.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Entity
@Table(name = "ALL_TXNS")
public class JPATransactionEntity extends AbsJPAEntity {

    @Column(name = "ORDER_ID", columnDefinition="VARCHAR(64)" )
    protected String orderId;


    public void setAttrs(JsonElement json) {
        super.setAttrs(json);
        JsonObject json2 = json.getAsJsonObject();
        if ( json2.has("orderId")) {
            orderId = json2.get("orderId").getAsString();
        }
    }


    public String getOrderId() {
        return orderId;
    }


    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }



}
