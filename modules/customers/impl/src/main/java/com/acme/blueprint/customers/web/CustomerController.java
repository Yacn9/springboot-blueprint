package com.acme.blueprint.customers.web;

import java.net.URI;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.application.CustomerFinder;
import com.acme.blueprint.customers.application.CustomerManagement;
import com.acme.blueprint.customers.application.CustomerSearchCriteria;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerStatus;
import com.acme.blueprint.customers.domain.CustomerTier;
import com.acme.blueprint.customers.domain.NationalCode;
import com.acme.blueprint.shared.ApiResponse;
import com.acme.blueprint.shared.EntityTags;
import com.acme.blueprint.shared.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST adapter for the customers module. It translates HTTP into use-case
 * calls and domain objects into response bodies — nothing more.
 *
 * <pre>
 * GET  /api/customers                       list: search, status, tier, page, size, sort=createdAt|lastName[,asc|desc]
 * POST /api/customers                       register                         → 201, Location, ETag
 * GET  /api/customers/{customerId}          read                             → ETag
 * PUT  /api/customers/{customerId}/profile  replace the profile  (If-Match)  → ETag
 * PUT  /api/customers/{customerId}/status   change the status    (If-Match)  → ETag
 * </pre>
 *
 * The two PUT sub-resources are write-only commands that return the updated
 * customer. Both require {@code If-Match} with the ETag last read, so two
 * operators editing the same customer cannot silently overwrite each other.
 */
@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private static final int SEARCH_MAX_LENGTH = 100;

    private final CustomerManagement customers;

    CustomerController(CustomerManagement customers) {
        this.customers = customers;
    }

    /** Sort keys are the finder's public vocabulary ({@link CustomerFinder}), never entity paths. */
    @GetMapping
    ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) @Size(max = SEARCH_MAX_LENGTH) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) CustomerTier tier,
            @PageableDefault(size = 20, sort = CustomerFinder.SORT_BY_CREATED_AT, direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var page = customers.search(new CustomerSearchCriteria(search, status, tier), pageable)
                .map(CustomerResponse::from);
        return ApiResponse.of(PageResponse.from(page));
    }

    @PostMapping
    ResponseEntity<ApiResponse<CustomerResponse>> register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer = customers.register(NationalCode.of(request.nationalCode()), request.profile().toDomain());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{customerId}")
                .buildAndExpand(customer.id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(EntityTags.of(customer.version()))
                .body(ApiResponse.of(CustomerResponse.from(customer)));
    }

    @GetMapping("/{customerId}")
    ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable CustomerId customerId) {
        return withETag(customers.get(customerId));
    }

    @PutMapping("/{customerId}/profile")
    ResponseEntity<ApiResponse<CustomerResponse>> updateProfile(
            @PathVariable CustomerId customerId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody CustomerProfileDto profile) {
        return withETag(customers.updateProfile(customerId, EntityTags.parseVersion(ifMatch), profile.toDomain()));
    }

    @PutMapping("/{customerId}/status")
    ResponseEntity<ApiResponse<CustomerResponse>> changeStatus(
            @PathVariable CustomerId customerId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ChangeCustomerStatusRequest request) {
        return withETag(customers.changeStatus(customerId, EntityTags.parseVersion(ifMatch), request.status()));
    }

    private static ResponseEntity<ApiResponse<CustomerResponse>> withETag(Customer customer) {
        return ResponseEntity.ok()
                .eTag(EntityTags.of(customer.version()))
                .body(ApiResponse.of(CustomerResponse.from(customer)));
    }
}
