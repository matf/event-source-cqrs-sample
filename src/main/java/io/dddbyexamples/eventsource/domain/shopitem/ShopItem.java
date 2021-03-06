package io.dddbyexamples.eventsource.domain.shopitem;

import com.google.common.collect.ImmutableList;
import io.dddbyexamples.eventsource.domain.shopitem.events.DomainEvent;
import io.dddbyexamples.eventsource.domain.shopitem.events.ItemBought;
import io.dddbyexamples.eventsource.domain.shopitem.events.ItemPaid;
import io.dddbyexamples.eventsource.domain.shopitem.events.ItemPaymentTimeout;
import lombok.Getter;
import lombok.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.dddbyexamples.eventsource.domain.shopitem.ShopItemState.*;

@Value
public class ShopItem {

    @Getter
    private final UUID uuid;
    private final ImmutableList<DomainEvent> changes;
    private final ShopItemState state;

    public ShopItem buy(UUID uuid, Instant when, int hoursToPaymentTimeout) {
        if (state == INITIALIZED) {
            return applyChange(new ItemBought(uuid, when, calculatePaymentTimeoutDate(when, hoursToPaymentTimeout)));
        } else {
            return this;
        }
    }

    private Instant calculatePaymentTimeoutDate(Instant boughtAt, int hoursToPaymentTimeout) {
        final Instant paymentTimeout = boughtAt.plus(hoursToPaymentTimeout, ChronoUnit.HOURS);
        if (paymentTimeout.isBefore(boughtAt)) {
            throw new IllegalArgumentException("Payment timeout day is before buying date!");
        }
        return paymentTimeout;
    }

    public ShopItem pay(Instant when) {
        throwIfStateIs(INITIALIZED, "Cannot pay for not existing item");
        if (state != PAID) {
            return applyChange(new ItemPaid(uuid, when));
        } else {
            return this;
        }
    }

    public ShopItem markTimeout(Instant when) {
        throwIfStateIs(INITIALIZED, "Payment is not missing yet");
        throwIfStateIs(PAID, "Item already paid");
        if (state == BOUGHT) {
            return applyChange(new ItemPaymentTimeout(uuid, when));
        } else {
            return this;
        }
    }

    private void throwIfStateIs(ShopItemState unexpectedState, String msg) {
        if (state == unexpectedState) {
            throw new IllegalStateException(msg + (" UUID: " + uuid));
        }
    }

    private ShopItem apply(ItemBought event) {
        return new ShopItem(event.getUuid(), changes, BOUGHT);
    }

    private ShopItem apply(ItemPaid event) {
        return new ShopItem(event.getUuid(), changes, PAID);
    }

    private ShopItem apply(ItemPaymentTimeout event) {
        return new ShopItem(event.getUuid(), changes, PAYMENT_MISSING);
    }

    public static ShopItem from(UUID uuid, List<DomainEvent> history) {
        return history
                .stream()
                .reduce(
                        new ShopItem(uuid, ImmutableList.of(), INITIALIZED),
                        (tx, event) -> tx.applyChange(event, false),
                        (t1, t2) -> {throw new UnsupportedOperationException();}
                );
    }

    private ShopItem applyChange(DomainEvent event, boolean isNew) {
        final ShopItem item = this.apply(event);
        if (isNew) {
            return new ShopItem(item.getUuid(), appendChange(item, event), item.getState());
        } else {
            return item;
        }
    }

    private ImmutableList<DomainEvent> appendChange(ShopItem item, DomainEvent event) {
        return ImmutableList
                .<DomainEvent>builder()
                .addAll(item.getChanges())
                .add(event)
                .build();
    }

    private ShopItem apply(DomainEvent event) {
        if (event instanceof ItemPaid) {
            return this.apply((ItemPaid) event);
        } else if (event instanceof ItemBought) {
            return this.apply((ItemBought) event);
        } else if (event instanceof ItemPaymentTimeout) {
            return this.apply((ItemPaymentTimeout) event);
        } else {
            throw new IllegalArgumentException("Cannot handle event " + event.getClass());
        }
    }

    private ShopItem applyChange(DomainEvent event) {
        return applyChange(event, true);
    }

    public ImmutableList<DomainEvent> getUncommittedChanges() {
        return changes;
    }

    public ShopItem markChangesAsCommitted() {
        return new ShopItem(uuid, ImmutableList.of(), state);
    }

}
