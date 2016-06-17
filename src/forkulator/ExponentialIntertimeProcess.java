package forkulator;

public class ExponentialIntertimeProcess extends IntertimeProcess {

	public double rate = 1.0;
	
	public ExponentialIntertimeProcess(double rate) {
		this.rate = rate;
	}
	
	@Override
	public double nextInterval(int jobSize) {
		return -Math.log(rand.nextDouble())/rate;
	}

	@Override
	public IntertimeProcess clone() {
		return new ExponentialIntertimeProcess(rate);
	}

}