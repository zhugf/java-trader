package trader.service.repository.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class JPAOrderEntity extends AbsJPAEntity {

}
