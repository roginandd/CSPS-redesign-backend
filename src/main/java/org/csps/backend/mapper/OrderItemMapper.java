package org.csps.backend.mapper;

import org.csps.backend.domain.dtos.request.OrderItemRequestDTO;
import org.csps.backend.domain.dtos.response.OrderItemResponseDTO;
import org.csps.backend.domain.entities.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(source = "order.orderId", target = "orderId")
    @Mapping(source = "order.student.studentId", target = "studentId")
    @Mapping(target = "studentName", expression = "java(getStudentFullName(orderItem))")
    @Mapping(source = "merchVariantItem.merchVariant.merch.merchName", target = "merchName")
    @Mapping(source = "merchVariantItem.merchVariant.merch.merchType", target = "merchType")
    @Mapping(source = "merchVariantItem.merchVariant.color", target = "color")
    @Mapping(source = "merchVariantItem.merchVariant.design", target = "design")
    @Mapping(source = "merchVariantItem.size", target = "size")
    @Mapping(source = "merchVariantItem.merchVariant.s3ImageKey", target = "s3ImageKey")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(target = "totalPrice", expression = "java(orderItem.getQuantity() * orderItem.getPriceAtPurchase())")
    @Mapping(target = "freebieAssignments", ignore = true)
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source="orderStatus", target="orderStatus")
    OrderItemResponseDTO toResponseDTO(OrderItem orderItem);

    @Mapping(source="merchVariantItemId", target="merchVariantItem.merchVariantItemId")
    @Mapping(source="orderId", target="order.orderId")
    OrderItem toEntity(OrderItemRequestDTO orderItemRequestDTO);
    
    /* safely extract student full name from nested relationships with null-checks */
    default String getStudentFullName(OrderItem orderItem) {
        if (orderItem == null || orderItem.getOrder() == null || orderItem.getOrder().getStudent() == null) {
            return "";
        }
        var student = orderItem.getOrder().getStudent();
        var userAccount = student.getUserAccount();
        if (userAccount == null || userAccount.getUserProfile() == null) {
            return "";
        }
        var profile = userAccount.getUserProfile();
        return (profile.getFirstName() != null ? profile.getFirstName() : "") + " " +
               (profile.getLastName() != null ? profile.getLastName() : "");
    }
}
