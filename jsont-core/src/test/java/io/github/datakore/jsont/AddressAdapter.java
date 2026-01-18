package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.entity.Address;

import java.util.List;

public class AddressAdapter implements SchemaAdapter<Address> {
    @Override
    public Class<Address> logicalType() {
        return Address.class;
    }

    @Override
    public Address createTarget() {
        return new Address();
    }


    @Override
    public void set(Object target, String fieldName, Object value) {
        Address address = (Address) target;
        switch (fieldName) {
            case "status":
                address.setStatus((String) value);
                break;
            case "zipCode":
                address.setZipCode((String) value);
                break;
            case "city":
            default:
                address.setCity((String) value);
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        Address address = (Address) target;
        switch (fieldName) {
            case "status":
                return address.getStatus();
            case "zipCode":
                return address.getZipCode();
            case "city":
            default:
                return address.getCity();
        }
    }
}
