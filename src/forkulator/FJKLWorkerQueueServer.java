package forkulator;

import java.util.LinkedList;
import java.util.Map;


public class FJKLWorkerQueueServer extends FJServer {
	
	/**
	 * The (k,l) server considers a job done when l of its k tasks complete.
	 * The remaining tasks are still left to run to completion.
	 * 
	 */
	public int l;

	boolean cancelLeftQueued;
	boolean cancelLeftRunning;
	
	/**
	 * This type of server has a separate queue for each worker.
	 * 
	 */
	private int worker_index = 0;
	
	
	/**
	 * Constructor
	 * 
	 * Allocate a FJJob queue for each worker.
	 * 
	 * @param num_workers
	 */
	public FJKLWorkerQueueServer(int num_workers, int l, boolean cancelLeftQueued, boolean cancelLeftRunning) {
		super(num_workers);
		
		if (l > num_workers) {
			System.err.println("ERROR: FJKLWorkerQueueServer cannot have l>k");
			System.exit(0);
		}
		
		this.l = l;
		this.cancelLeftQueued = cancelLeftQueued;
		this.cancelLeftRunning = cancelLeftRunning;
		for (int i=0; i<num_workers; i++) {
			workers[0][i].queue = new LinkedList<FJTask>();
		}
	}

	public FJKLWorkerQueueServer(int num_workers, int l) {
		this(num_workers,l,true,true);
	}

	/**
	 * Check for any idle workers and try to put a task on them.
	 * 
	 * @param time
	 */
	public void feedWorkers(double time) {
		// check for idle workers
		for (int i=0; i<num_workers; i++) {
			if (workers[0][i].current_task == null) {
				// if the worker is idle, pull the next task (or null) from its queue
				serviceTask(workers[0][i], workers[0][i].queue.poll(), time);
			}
		}
	}
	
	
	/**
	 * Enqueue a new job.
	 * 
	 * This type of server has a separate queue for each worker.  When a job arrives
	 * we immediately assign its tasks to the workers' queues.
	 * 
	 * This part should behave just like WQ server.
	 * 
	 * @param job
	 * @param sample
	 */
	public void enqueJob(FJJob job, boolean sample) {
		if (FJSimulator.DEBUG) System.out.println("enqueJob() "+job.arrival_time);

		// only keep a reference to the job if the simulator tells us to
		job.setSample(sample);

		FJTask t = null;
		while ((t = job.nextTask()) != null) {
			workers[0][worker_index].queue.add(t);
			job.queuing_tasks.put(workers[0][worker_index],t);
			worker_index = (worker_index + 1) % num_workers;
		}
		
		// this just added the tasks to the queues.  Check if any
		// workers were idle, and put them to work.
		feedWorkers(job.arrival_time);
	}

	/**
	 * Put the task on the specified worker and schedule its completion in the event queue.
	 *
	 * @param workerId
	 * @param task
	 * @param time
	 */
	public void serviceTask(FJWorker worker, FJTask task, double time) {
		//if (FJSimulator.DEBUG) System.out.println("serviceTask() "+task.ID);
		worker.current_task = task;
		if (task != null) {
			task.worker = worker;
			task.start_time = time;
			task.processing = true;
			if(task.job!=null){//skip orphan tasks.
				task.job.queuing_tasks.remove(worker);
				task.job.running_tasks.put(worker,task);
			}
			// schedule the task's completion
			QTaskCompletionEvent e = new QTaskCompletionEvent(task, time + task.service_time);
			simulator.addEvent(e);
		}
	}
	/**
	 * Handle a task completion event.  Remove the task from its worker, and
	 * give the worker a new task, if available.
	 * 
	 * With in the (k,l) server we consider a job to be complete and
	 * departed when k of its tasks are complete.  But we let the other
	 * tasks keep running.  Do we need to keep the completed jobs around until
	 * all their tasks finish (if they aren't sampled)?
	 * 
	 * @param workerId
	 * @param time
	 */
	public void taskCompleted(FJWorker worker, double time) {
		//if (FJSimulator.DEBUG) System.out.println("task "+worker.current_task.ID+" completed "+time);
		FJTask task = worker.current_task;
		task.completion_time = time;
		task.completed = true;
		if (task.job!=null) {//skip orphan completed tasks.
			task.job.running_tasks.remove(worker);
			task.job.completed_tasks.put(worker,task);
			if (task.job.completed_tasks.size()==this.l) {// check if this task is the l'th of the job.
				task.job.completed = true;
				task.job.completion_time = time;
				task.job.departure_time = time;
				if (this.cancelLeftQueued) {
					for (Map.Entry<FJWorker, FJTask> entry : task.job.queuing_tasks.entrySet()) {
						entry.getKey().queue.remove(entry.getValue());
					}
				}
				if (this.cancelLeftRunning) {
					for (Map.Entry<FJWorker, FJTask> entry : task.job.running_tasks.entrySet()) {
						serviceTask(entry.getKey(),entry.getKey().queue.poll(), time);
					}
				}
				jobDepart(task.job);
				//System.out.println("Job "+task.job.ID+" completed at "+time);
			}
		}
		// try to start servicing a new task on this worker
		serviceTask(worker, worker.queue.poll(), time);
	}
	
	
	/**
	 * In the multi-queue server we take the queue length to be the rounded average
	 * length of all the worker queues.
	 */
	@Override
	public int queueLength() {
		int lsum = 0;
		for (FJWorker w : workers[0])
			lsum += w.queue.size();

		return (lsum / num_workers);
	}
	
}
