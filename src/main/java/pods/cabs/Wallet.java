package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Wallet extends AbstractBehavior<Wallet.Command> {
    public interface Command {
    }

    public static Behavior<Command> create(String custId, int balance) {
        return Behaviors.setup(context -> new Wallet(context, custId, balance));
    }

    private String custId;      // customer Id
    private int balance;        // amount in customer wallet
    private int initialBalance; // initial balance, keeping for purpose of resetting to initialBalance
    
    private Wallet(ActorContext<Command> context, String custId, int balance) {
        super(context);
        this.custId = custId;
        this.balance = balance;
        this.initialBalance = balance;

        context.getLog().info("created wallet actor for {}", custId);
    }

    // GetBalance message, sent to get balance
    public static final class GetBalance implements Command {
        ActorRef<Wallet.ResponseBalance> replyTo;

        GetBalance(ActorRef<Wallet.ResponseBalance> replyTo) {
            this.replyTo = replyTo;
        }
    }

    // ResponseBalance message is used to reply with balance
    public static final class ResponseBalance implements Command {
        int balance;
        
        ResponseBalance(int balance) {
            this.balance = balance;
        }
    }

    // DeductBalance message is sent to deduct balance from wallet and send balance
    public static final class DeductBalance implements Command {
        ActorRef<Wallet.ResponseBalance> replyTo;
        int toDeduct;

        DeductBalance(int toDeduct, ActorRef<Wallet.ResponseBalance> replyTo) {
            this.replyTo = replyTo;
            this.toDeduct = toDeduct;
        }
    }

    // AddBalance message is sent to add balance to wallet
    public static final class AddBalance implements Command {
        int toAdd;

        AddBalance(int toAdd) {
            this.toAdd = toAdd;
        }
    }

    // Reset message is used to reset wallet to initial balance
    public static final class Reset implements Command {
        ActorRef<Wallet.ResponseBalance> replyTo;

        Reset(ActorRef<ResponseBalance> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetBalance.class, this::onGetBalance)
            .onMessage(DeductBalance.class, this::onDeductBalance)
            .onMessage(AddBalance.class, this::onAddBalance)
            .onMessage(Reset.class, this::onReset)
            .build();
    }

    // Reset message handler, resets the wallet balance to initial balance
    // sends a ResponseBalance message with current balance
    private Wallet onReset(Reset command) {
        getContext().getLog().info("Reset wallet");
        this.balance = this.initialBalance;
        command.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

    // GetBalance message handler
    // sends a ResponseBalance message with current balance
    private Wallet onGetBalance(GetBalance command) throws Exception {
        getContext().getLog().info("Seeking the balance in WalletGetBalance for  cust {}", this.custId);
        command.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

    // DeductBalance message handler
    // sends a ResponseBalance message with current balance
    private Wallet onDeductBalance(DeductBalance command) {
        getContext().getLog().info("Wallet.Deductbalance {} ", command.toDeduct);
        // check for negative deductAmount and balance must be greater than deductAmount
        // if balance deduction could not be performed due to insignificant balance,
        // the balance is not disturbed and -1 is sent back in the response. 
        if ((command.toDeduct < 0) || (command.toDeduct > this.balance)) {
            command.replyTo.tell(new ResponseBalance(-1));
            return this;
        }
        this.balance -= command.toDeduct;
        command.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

    // AddBalance message handler
    private Wallet onAddBalance(AddBalance command) {
        getContext().getLog().info("Adding balance {}", command.toAdd);
        // if add amount is negative, the balance is not disturbed
        if (command.toAdd < 0) {
            return this;
        }
        this.balance += command.toAdd;
        return this;
    }

}
