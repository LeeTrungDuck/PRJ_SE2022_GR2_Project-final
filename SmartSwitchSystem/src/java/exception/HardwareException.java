/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Exception.java to edit this template
 */
package exception;

/**
 *
 * @author ltrun
 */
public class HardwareException extends Exception{

    /**
     * Creates a new instance of <code>HardwareException</code> without detail
     * message.
     */
    public HardwareException() {
    }

    /**
     * Constructs an instance of <code>HardwareException</code> with the
     * specified detail message.
     *
     * @param msg the detail message.
     */
    public HardwareException(String msg) {
        super(msg);
    }
}
